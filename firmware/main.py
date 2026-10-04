#!/usr/bin/env python3
"""
ResQMesh ESP32 Node Firmware & Relay Engine
Designed for MicroPython on ESP32 / ESP32-S3 Nodes.
Includes full off-grid Mesh Relaying, ESP-NOW 2.4GHz Radio Broadcast,
BLE Nordic UART Service (NUS) Mobile Gateway with gatts_write + gatts_notify,
USB Serial Non-Blocking Stdin Listener for Laptop Base Station,
Wi-Fi SoftAP HTTP Telemetry, and Sequence ID Deduplication with TTL Decrement.
"""

import sys
import time
import random

# Hardware platform detection
IS_MICROPYTHON = hasattr(sys, 'implementation') and sys.implementation.name == 'micropython'

if IS_MICROPYTHON:
    import network
    import ubinascii
    import machine
    import select
    try:
        import espnow
    except ImportError:
        espnow = None
    try:
        import ubluetooth
    except ImportError:
        ubluetooth = None
    try:
        import socket
    except ImportError:
        socket = None
else:
    import socket
    import threading

# Configuration
DEFAULT_NODE_ID = "RQ50EE"
DEFAULT_LAT = 13.000260
DEFAULT_LON = 74.796070
WIFI_SSID_PREFIX = "ResQMesh_"
WIFI_PASS = "resqmesh123"
WIFI_CHANNEL = 1
HTTP_PORT = 8080

# NUS UUIDs
NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
NUS_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

class ResQMeshNode:
    def __init__(self, node_id=None):
        self.node_id = node_id or self._auto_generate_node_id()
        self.lat = DEFAULT_LAT
        self.lon = DEFAULT_LON
        self.seen_seq_ids = []
        self.max_seen_history = 100
        self.telemetry_buffer = []
        self.max_telemetry_history = 50

        # BLE RX Fragment Buffer
        self.ble_rx_buffer = ""

        # Hardware interfaces
        self.e_now = None
        self.ble = None
        self.ble_conn_handle = None
        self.tx_handle = None
        self.rx_handle = None

        print(f"==================================================")
        print(f"⚡ ResQMesh Node [{self.node_id}] Initializing...")
        print(f"==================================================")

        self._init_wifi_ap_and_espnow()
        self._init_ble_nus()

    def _auto_generate_node_id(self):
        if IS_MICROPYTHON:
            try:
                mac = ubinascii.hexlify(network.WLAN(network.STA_IF).config('mac')).decode('utf-8').upper()
                return f"RQ{mac[-4:]}"
            except Exception:
                pass
        return DEFAULT_NODE_ID

    def _init_wifi_ap_and_espnow(self):
        if not IS_MICROPYTHON:
            print("[SIMULATION] Wi-Fi & ESP-NOW running in socket mesh simulation mode.")
            return

        try:
            # 1. Config STA and AP interfaces
            sta = network.WLAN(network.STA_IF)
            sta.active(True)
            sta.disconnect()

            ap = network.WLAN(network.AP_IF)
            ap.active(True)
            ap_ssid = f"{WIFI_SSID_PREFIX}{self.node_id}"
            ap.config(essid=ap_ssid, password=WIFI_PASS, channel=WIFI_CHANNEL)

            print(f"⚡ Wi-Fi SoftAP active: SSID='{ap_ssid}' (IP={ap.ifconfig()[0]})")

            # 2. Config ESP-NOW
            if espnow:
                self.e_now = espnow.ESPNow()
                self.e_now.active(True)
                # Add broadcast peer ff:ff:ff:ff:ff:ff safely
                try:
                    self.e_now.add_peer(b'\xff\xff\xff\xff\xff\xff')
                except Exception:
                    pass
                print("⚡ ESP-NOW 2.4GHz Mesh Radio Engine online!")
        except Exception as e:
            print(f"[WIFI/ESP-NOW ERR] {e}")

    def _init_ble_nus(self):
        if not IS_MICROPYTHON or not ubluetooth:
            print("[SIMULATION] BLE NUS running in mock mode.")
            return

        try:
            self.ble = ubluetooth.BLE()
            self.ble.active(True)
            self.ble.irq(self._ble_irq_handler)

            # Register NUS Service
            nus_uuid = ubluetooth.UUID(NUS_SERVICE_UUID)
            rx_uuid = ubluetooth.UUID(NUS_RX_UUID)
            tx_uuid = ubluetooth.UUID(NUS_TX_UUID)

            rx_char = (rx_uuid, ubluetooth.FLAG_WRITE | ubluetooth.FLAG_WRITE_NO_RESPONSE)
            tx_char = (tx_uuid, ubluetooth.FLAG_READ | ubluetooth.FLAG_NOTIFY)
            nus_service = (nus_uuid, (rx_char, tx_char),)

            services = (nus_service,)
            ((self.rx_handle, self.tx_handle),) = self.ble.gatts_register_services(services)

            self._start_ble_advertising()
            print(f"⚡ BLE NUS Server advertising as '{WIFI_SSID_PREFIX}{self.node_id}'!")
        except Exception as e:
            print(f"[BLE INIT ERR] {e}")

    def _start_ble_advertising(self):
        if not self.ble:
            return
        name = f"RQ_{self.node_id[-4:]}"
        adv_data = bytearray([
            0x02, 0x01, 0x06,  # General discoverable
            len(name) + 1, 0x09  # Complete local name
        ]) + name.encode('utf-8')
        try:
            self.ble.gap_advertise(100000, adv_data)
        except Exception as e:
            print(f"[ADV ERR] {e}")

    def _ble_irq_handler(self, event, data):
        if event == 1:  # _IRQ_CENTRAL_CONNECT
            conn_handle, _, _ = data
            self.ble_conn_handle = conn_handle
            self.ble_rx_buffer = ""
            print(f"⚡ Mobile Phone connected over BLE GATT (handle={conn_handle})!")
        elif event == 2:  # _IRQ_CENTRAL_DISCONNECT
            self.ble_conn_handle = None
            self.ble_rx_buffer = ""
            print("BLE Mobile disconnected. Restarting BLE advertising...")
            self._start_ble_advertising()
        elif event == 3:  # _IRQ_GATTS_WRITE
            conn_handle, value_handle = data
            if value_handle == self.rx_handle:
                chunk = self.ble.gatts_read(self.rx_handle).decode('utf-8', 'ignore')
                self.ble_rx_buffer += chunk
                while '\n' in self.ble_rx_buffer:
                    line, self.ble_rx_buffer = self.ble_rx_buffer.split('\n', 1)
                    if line.strip():
                        self.handle_incoming_raw_line(line.strip(), source="BLE")

    def notify_ble_phone(self, line):
        if self.ble and self.ble_conn_handle is not None and self.tx_handle is not None:
            try:
                line_str = str(line)
                payload = line_str if line_str.endswith('\n') else line_str + '\n'
                data_bytes = payload.encode('utf-8')

                # 1. Update Characteristic Value in GATT Database
                self.ble.gatts_write(self.tx_handle, data_bytes)

                # 2. Notify Central Device (Phone) over BLE GATT
                self.ble.gatts_notify(self.ble_conn_handle, self.tx_handle, data_bytes)
                print(f"⚡ [BLE NOTIFIED PHONE] Transmitted: '{payload.strip()}'")
            except Exception as e:
                print(f"[BLE NOTIFY ERR] {e}")

    def handle_incoming_raw_line(self, raw_line, source="UNKNOWN"):
        raw_line = raw_line.strip()
        if not raw_line:
            return

        # Strip transport prefixes
        for prefix in ["PC_INGEST:", "PC_SEND:", "RAW,", "RAW:", "TX:", "RX:", "INGEST:"]:
            if raw_line.startswith(prefix):
                raw_line = raw_line[len(prefix):].strip()

        parts = raw_line.split("|")
        if len(parts) < 8:
            print(f"[PARSE DROP] Invalid frame format ({len(parts)} fields): '{raw_line}'")
            return

        mtype = parts[0].strip()
        src_id = parts[1].strip()
        dest_id = parts[2].strip()
        fam_id = parts[3].strip()
        prio = parts[4].strip()
        try:
            lat = float(parts[5].strip()) if parts[5].strip() else self.lat
            lon = float(parts[6].strip()) if parts[6].strip() else self.lon
        except ValueError:
            lat, lon = self.lat, self.lon

        text = parts[7].strip()

        # Parse TTL and Seq ID
        ttl = 4
        if len(parts) > 8 and parts[8].strip():
            try:
                ttl = int(parts[8].strip())
            except ValueError:
                ttl = 4

        seq_id = ""
        if len(parts) > 9 and parts[9].strip():
            seq_id = parts[9].strip()
        else:
            seq_id = f"{random.randint(1000, 9999):04X}"

        # Sequence ID Deduplication
        if seq_id in self.seen_seq_ids:
            # Duplicate frame received from another node - drop to prevent infinite loops!
            return

        self.seen_seq_ids.append(seq_id)
        if len(self.seen_seq_ids) > self.max_seen_history:
            self.seen_seq_ids.pop(0)

        # Print raw 10-field pipe-delimited frame to Base Station USB Serial
        reconstructed_frame = f"{mtype}|{src_id}|{dest_id}|{fam_id}|{prio}|{lat:.6f}|{lon:.6f}|{text}|{ttl}|{seq_id}"
        print(reconstructed_frame)
        try:
            sys.stdout.write(reconstructed_frame + "\n")
        except Exception:
            pass

        # Buffer for HTTP telemetry GET requests
        self.telemetry_buffer.insert(0, reconstructed_frame)
        if len(self.telemetry_buffer) > self.max_telemetry_history:
            self.telemetry_buffer.pop()

        # Relay over BLE TX to connected mobile phone
        if source != "BLE":
            self.notify_ble_phone(reconstructed_frame)

        # MESH HOPPING / RELAY: Decrement TTL & Re-Broadcast over ESP-NOW Radio to Neighboring Nodes
        new_ttl = ttl - 1
        if new_ttl > 0:
            relayed_frame = f"{mtype}|{src_id}|{dest_id}|{fam_id}|{prio}|{lat:.6f}|{lon:.6f}|{text}|{new_ttl}|{seq_id}"
            self._broadcast_espnow(relayed_frame)

        # AUTO-ACK ON HI / PING BROADCASTS: Every node responds with unique node ACK frame
        if src_id != self.node_id and not text.startswith("ACK_"):
            if "HI" in text.upper() or "PING" in text.upper():
                ack_seq = f"ack_{self.node_id}_{random.randint(1000, 9999):04X}"
                ack_text = f"ACK_HI_FROM_{self.node_id}"
                ack_frame = f"MSG|{self.node_id}|{src_id}|RESPONSE|P2|{self.lat:.6f}|{self.lon:.6f}|{ack_text}|4|{ack_seq}"
                self.handle_incoming_raw_line(ack_frame, source="AUTO_ACK")

    def _broadcast_espnow(self, payload_str):
        if IS_MICROPYTHON and self.e_now:
            msg_bytes = (payload_str + "\n").encode('utf-8')
            try:
                self.e_now.send(b'\xff\xff\xff\xff\xff\xff', msg_bytes)
            except Exception as e1:
                try:
                    self.e_now.send(None, msg_bytes)
                except Exception as e2:
                    print(f"[ESP-NOW TX ERR] {e1} / {e2}")

    def send_local_beacon(self):
        seq_id = f"bcn_{random.randint(1000, 9999):04X}"
        beacon_frame = f"BEACON|{self.node_id}|ALL|NONE|P3|{self.lat:.6f}|{self.lon:.6f}|NODE_HEARTBEAT_ACTIVE|4|{seq_id}"
        self.handle_incoming_raw_line(beacon_frame, source="LOCAL_BEACON")

    def run_poll_loop(self):
        last_beacon_time = time.time()
        print(f"\n==================================================")
        print(f"🚀 ResQMesh Node [{self.node_id}] RUNNING & RELAYING!")
        print(f"==================================================\n")

        while True:
            # 1. Poll ESP-NOW Radio
            if IS_MICROPYTHON and self.e_now:
                try:
                    host, msg = self.e_now.recv(0)
                    if msg:
                        line = msg.decode('utf-8', 'ignore')
                        self.handle_incoming_raw_line(line, source="ESP-NOW_RADIO")
                except Exception:
                    pass

            # 2. Poll USB Serial Stdin from Laptop Base Station
            if IS_MICROPYTHON:
                try:
                    r, _, _ = select.select([sys.stdin], [], [], 0)
                    if r:
                        line = sys.stdin.readline()
                        if line:
                            self.handle_incoming_raw_line(line.strip(), source="USB_SERIAL")
                except Exception:
                    pass

            # 3. Automated Heartbeat Beacon every 30 seconds
            if time.time() - last_beacon_time >= 30:
                self.send_local_beacon()
                last_beacon_time = time.time()

            time.sleep(0.05)

if __name__ == '__main__':
    node = ResQMeshNode()
    node.run_poll_loop()

