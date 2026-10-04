/*
 * ResQMesh ESP32 Physical Node Firmware (Arduino IDE / C++)
 * Flash this code onto EACH physical ESP32 board in your mesh network.
 *
 * Features:
 *  1. ESP-NOW 2.4GHz Radio Mesh Broadcast (Hop-by-hop relay locked to WiFi Ch 1)
 *  2. Sequence ID Deduplication (Prevents loop storms)
 *  3. TTL (Time To Live) Decrement per hop
 *  4. BLE Nordic UART Service (NUS) with 20-byte RX/TX Chunk Line Buffering
 *  5. Auto Advertising Stop on Connect (Prevents multi-phone GATT collisions)
 *  6. Protected Pending Buffer (Beacons cannot overwrite human SOS frames)
 *  7. Serial USB Bi-Directional Bridge for Incident Command Base Station laptop
 */

#include <WiFi.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Unique Node ID (Derived automatically from MAC address)
String NODE_ID = "RQ10A1";
float NODE_LAT = 13.000260;
float NODE_LON = 74.796070;

// BLE Nordic UART Service (NUS) UUIDs
#define SERVICE_UUID           "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic = NULL;
bool deviceConnected = false;

// Broadcast MAC Address for ESP-NOW (ff:ff:ff:ff:ff:ff)
uint8_t broadcastAddress[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

// Deduplication Ring Buffer
String seenSeqIds[100];
int seqIndex = 0;

bool isDuplicate(String seqId) {
  if (seqId.length() == 0) return false;
  for (int i = 0; i < 100; i++) {
    if (seenSeqIds[i] == seqId) return true;
  }
  seenSeqIds[seqIndex] = seqId;
  seqIndex = (seqIndex + 1) % 100;
  return false;
}

// Function Declarations
void processAndRelayFrame(String rawFrame, String source);
void sendEspNowBroadcast(String msg);
void sendBleNotification(String payload);

String lastPendingFrame = "";
String bleRxBuffer = ""; // Accumulates incoming 20-byte BLE chunks until '\n'

// BLE Callbacks
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      bleRxBuffer = "";
      Serial.println("⚡ [BLE] Mobile Phone Connected! Stopping advertising to prevent secondary phone collisions.");
      if (pServer && pServer->getAdvertising()) {
        pServer->getAdvertising()->stop();
      }
      if (lastPendingFrame.length() > 0 && pTxCharacteristic) {
        Serial.println("⚡ [BLE] Flushing pending radio frame to newly connected phone...");
        delay(100);
        sendBleNotification(lastPendingFrame);
        lastPendingFrame = "";
      }
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      bleRxBuffer = "";
      Serial.println("⚡ [BLE] Mobile Phone Disconnected! Restarting Advertising...");
      if (pServer && pServer->getAdvertising()) {
        pServer->getAdvertising()->start();
      }
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();
      if (rxValue.length() > 0) {
        bleRxBuffer += rxValue;
        int newlineIdx = bleRxBuffer.indexOf('\n');
        while (newlineIdx != -1) {
          String completeFrame = bleRxBuffer.substring(0, newlineIdx);
          completeFrame.trim();
          bleRxBuffer = bleRxBuffer.substring(newlineIdx + 1);
          if (completeFrame.length() > 0) {
            Serial.println("⚡ [BLE INGEST COMPLETE] " + completeFrame);
            processAndRelayFrame(completeFrame, "BLE_MOBILE_APP");
          }
          newlineIdx = bleRxBuffer.indexOf('\n');
        }
        if (bleRxBuffer.length() > 1024) {
          bleRxBuffer = ""; // Safety overflow reset
        }
      }
    }
};

// ESP-NOW Receive Callback (Supports ESP32 Arduino Core 2.x and 3.x)
#if defined(ESP_ARDUINO_VERSION) && ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
void OnDataRecv(const esp_now_recv_info_t *info, const uint8_t *incomingData, int len) {
#else
void OnDataRecv(const uint8_t * mac_addr, const uint8_t *incomingData, int len) {
#endif
  char buf[256];
  int copyLen = min(len, 255);
  memcpy(buf, incomingData, copyLen);
  buf[copyLen] = '\0';
  String msg = String(buf);
  processAndRelayFrame(msg, "ESP_NOW_RADIO");
}

void processAndRelayFrame(String rawFrame, String source) {
  rawFrame.trim();
  if (rawFrame.length() == 0) return;

  // Strip transport prefixes
  String prefixes[] = {"PC_INGEST:", "PC_SEND:", "RAW,", "RAW:", "TX:", "RX:", "INGEST:"};
  for (String p : prefixes) {
    if (rawFrame.startsWith(p)) {
      rawFrame = rawFrame.substring(p.length());
      rawFrame.trim();
    }
  }

  // Parse Pipe-Delimited Fields: mtype|src_id|dest_id|fam_id|prio|lat|lon|text|ttl|seq_id|path
  String parts[12];
  int partCount = 0;
  int startIdx = 0;

  for (int i = 0; i < rawFrame.length(); i++) {
    if (rawFrame.charAt(i) == '|') {
      if (partCount < 12) {
        parts[partCount++] = rawFrame.substring(startIdx, i);
      }
      startIdx = i + 1;
    }
  }
  if (startIdx < rawFrame.length() && partCount < 12) {
    parts[partCount++] = rawFrame.substring(startIdx);
  }

  if (partCount < 8) {
    Serial.println("⚠️ [INVALID FRAME] Incomplete frame (" + String(partCount) + " parts): " + rawFrame);
    return;
  }

  String mtype  = parts[0];
  String srcId  = parts[1];
  String destId = parts[2];
  String famId  = parts[3];
  String prio   = parts[4];
  String lat    = parts[5];
  String lon    = parts[6];
  String text   = parts[7];
  text.replace("|", " - "); // Sanitize text payload
  int ttl       = (partCount > 8) ? parts[8].toInt() : 4;
  String seqId  = (partCount > 9 && parts[9].length() > 0) ? parts[9] : String(random(1000, 9999), HEX);
  String path   = (partCount > 10) ? parts[10] : srcId;

  if (ttl <= 0) ttl = 4;

  // Check Deduplication
  if (isDuplicate(seqId)) {
    Serial.println("🔁 [DEDUP DROPPED] Duplicate frame ignored (seq_id: " + seqId + ")");
    return;
  }

  // Append this node to path if not already present
  String currentPath = path;
  if (!currentPath.endsWith(NODE_ID) && currentPath != NODE_ID) {
    currentPath += ">" + NODE_ID;
  }

  // Reconstruct 11-field standard frame
  String reconstructed = mtype + "|" + srcId + "|" + destId + "|" + famId + "|" + prio + "|" + lat + "|" + lon + "|" + text + "|" + String(ttl) + "|" + seqId + "|" + currentPath;

  Serial.println("⚡ [PROCESSED FRAME] (" + source + "): " + reconstructed);

  // Send to Connected Mobile Phone over BLE TX with 20-byte chunking
  if (deviceConnected && pTxCharacteristic) {
    sendBleNotification(reconstructed);
  } else if (mtype != "BEACON" && !text.contains("NODE_ACTIVE")) {
    lastPendingFrame = reconstructed;
    Serial.println("⚡ [BLE BUFFERED] Phone not connected, saved human message frame for next client connect.");
  }

  // RELAY HOP: Decrement TTL & Broadcast over ESP-NOW to neighbor nodes
  int newTtl = ttl - 1;
  if (newTtl > 0) {
    String relayedFrame = mtype + "|" + srcId + "|" + destId + "|" + famId + "|" + prio + "|" + lat + "|" + lon + "|" + text + "|" + String(newTtl) + "|" + seqId + "|" + currentPath;
    sendEspNowBroadcast(relayedFrame);
  }
}

void sendBleNotification(String payload) {
  if (!deviceConnected || !pTxCharacteristic) return;
  if (!payload.endsWith("\n")) payload += "\n";
  int len = payload.length();
  int offset = 0;
  while (offset < len) {
    int chunkSize = min(20, len - offset);
    String chunk = payload.substring(offset, offset + chunkSize);
    pTxCharacteristic->setValue(chunk.c_str());
    pTxCharacteristic->notify();
    offset += chunkSize;
    delay(15);
  }
}

void sendEspNowBroadcast(String msg) {
  if (!msg.endsWith("\n")) msg += "\n";
  esp_err_t result = esp_now_send(broadcastAddress, (uint8_t *)msg.c_str(), msg.length());
  if (result == ESP_OK) {
    Serial.println("⚡ [ESP-NOW BROADCAST SUCCESS] " + msg);
  } else {
    Serial.println("❌ [ESP-NOW ERROR] Code: " + String(result));
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // 1. Lock WiFi Hardware Radio to Channel 1
  WiFi.mode(WIFI_STA);
  esp_wifi_set_promiscuous(true);
  esp_wifi_set_channel(1, WIFI_SECOND_CHAN_NONE);
  esp_wifi_set_promiscuous(false);

  // Generate Node ID from MAC address
  String mac = WiFi.macAddress();
  mac.replace(":", "");
  if (mac.length() >= 4 && !mac.endsWith("0000")) {
    NODE_ID = "RQ" + mac.substring(mac.length() - 4);
  } else {
    uint64_t chipid = ESP.getEfuseMac();
    char chipStr[10];
    snprintf(chipStr, sizeof(chipStr), "%04X", (uint16_t)(chipid >> 32));
    NODE_ID = "RQ" + String(chipStr);
  }

  Serial.println("==========================================");
  Serial.println("⚡ ResQMesh Node [" + NODE_ID + "] Starting...");
  Serial.println("==========================================");

  if (esp_now_init() != ESP_OK) {
    Serial.println("❌ Error initializing ESP-NOW");
    return;
  }
  esp_now_register_recv_cb(OnDataRecv);

  esp_now_peer_info_t peerInfo = {};
  memcpy(peerInfo.peer_addr, broadcastAddress, 6);
  peerInfo.channel = 0; // Match active channel
  peerInfo.encrypt = false;
  esp_now_add_peer(&peerInfo);

  // 2. BLE NUS Setup
  String bleDeviceName = "ResQMesh_" + NODE_ID;
  BLEDevice::init(bleDeviceName.c_str());
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                          CHARACTERISTIC_UUID_RX,
                                          BLECharacteristic::PROPERTY_WRITE |
                                          BLECharacteristic::PROPERTY_WRITE_NR
                                        );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();
  BLEAdvertising *pAdvertising = pServer->getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  pAdvertising->start();

  Serial.println("⚡ ESP-NOW Radio (WiFi Ch 1) & BLE Service ('" + bleDeviceName + "') ACTIVE!");
}

unsigned long lastBeacon = 0;

void loop() {
  while (Serial.available() > 0) {
    String serialLine = Serial.readStringUntil('\n');
    serialLine.trim();
    if (serialLine.length() > 0) {
      processAndRelayFrame(serialLine, "USB_SERIAL_LAPTOP");
    }
  }

  // Heartbeat Beacon every 30 seconds
  if (millis() - lastBeacon > 30000) {
    lastBeacon = millis();
    String seqId = "bcn_" + String(random(1000, 9999), HEX);
    String beaconFrame = "BEACON|" + NODE_ID + "|ALL|NONE|P3|" + String(NODE_LAT, 6) + "|" + String(NODE_LON, 6) + "|NODE_ACTIVE|4|" + seqId;
    processAndRelayFrame(beaconFrame, "LOCAL_BEACON");
  }
  delay(20);
}
