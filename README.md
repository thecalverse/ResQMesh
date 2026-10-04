# 🛰️ ResQMesh: Complete Off-Grid Disaster Mesh Communications System

---

## 📖 1. Executive Summary & Vision
**ResQMesh** is a zero-infrastructure, off-grid disaster communications platform engineered for emergency rescue scenarios where cellular towers, Wi-Fi routers, and internet backbones have failed (earthquakes, floods, network blackouts). 

The system forms an **autonomous, self-healing 2.4 GHz radio mesh network** using low-cost ESP32 microcontrollers, mobile Android smartphones (over BLE GATT), and an Incident Command Base Station laptop tethered via USB Serial.

### Core Goal / What We Want to Achieve:
* Allow **Civilians** to send life-safety SOS alerts, GPS coordinates, and messages directly from their phones.
* Have **ESP32 Nodes** hop these messages across 2.4 GHz radio waves (**ESP-NOW**) from node to node without internet or Wi-Fi routers.
* Deliver the SOS alerts to **Rescuers' phones** (which automatically plot the victim's location on OpenStreetMap and point a vector compass arrow toward them) and to the **Incident Command Base Station Laptop** (running a live Web Dashboard at `http://localhost:5050`).

---

## 🏗️ 2. System Architecture & Topology

```
 [Command Center Laptop]
          │
      USB Serial (115200 baud)
          │
          ▼
   [ESP32 Node 1] ◄────── ESP-NOW (2.4GHz Radio Ch 1) ──────► [ESP32 Node 3]
  (Base Gateway RQ50EE)                                       (Field Node B)
          ▲                                                         │
          │                                                         │ BLE GATT
          │ ESP-NOW (2.4GHz Radio Ch 1)                             │ (20-byte chunks)
          │                                                         ▼
   [ESP32 Node 2] ◄──────────── BLE GATT ────────────► [Phone 2 (ResQMesh App)]
   (Field Node A)             (20-byte chunks)
          ▲
          │ BLE GATT
          │ (20-byte chunks)
          ▼
 [Phone 1 (ResQMesh App)]
```

---

## ✅ 3. What Has Been Completed & Fixed

### A. ESP32 Firmware Improvements (`firmware/ResQMesh_ESP32_Node/ResQMesh_ESP32_Node.ino`)
1. **20-Byte BLE RX Line Buffer (`bleRxBuffer`)**:
   * *Problem Solved*: Android sends BLE packets in 20-byte GATT chunks. Previously, ESP32 tried to parse incomplete 20-byte chunks as full 11-field mesh frames and dropped 100% of incoming phone messages.
   * *Solution*: The ESP32 now accumulates incoming BLE bytes until a newline (`
`) is received before parsing and relaying the full frame.
2. **Auto-Advertising Pause on Connect**:
   * *Problem Solved*: Dual phones bouncingly collided on the same ESP32 node, causing GATT 133 errors.
   * *Solution*: When a phone connects to Node A, Node A immediately calls `pServer->getAdvertising()->stop()`, hiding itself so secondary phones seamlessly connect to neighboring available ESP32 nodes.
3. **Wi-Fi Hardware Channel Locking**:
   * Hardware radio is locked to **Channel 1** (`WIFI_STA` mode with locked promiscuous state) so all nodes remain on the exact same radio frequency.
4. **Protected Pending Frame Buffer**:
   * Background 30-second `BEACON` heartbeats are blocked from overwriting pending human `ALERT` / `SOS` messages if a phone is temporarily reconnecting.

### B. Android Application Fixes (`BleManager.kt`, `TacticalViewModel.kt`, `MainActivity.kt`)
1. **Universal 20-byte BLE Write Chunking (`WRITE_TYPE_NO_RESPONSE`)**:
   * Configured GATT writes with a fallback to avoid `GATT_BUSY` errors on Android 11, 12, 13, 14, and 15 across Samsung, Oppo, Vivo, and Pixel devices.
2. **Prevented BLE Feedback Ping-Pong Echo**:
   * `TacticalViewModel.kt` now checks `source != "BLE"` before re-transmitting over BLE, preventing endless duplicate message loops between the phone and local ESP32 node.
3. **Fixed SOS Payload Delimiter Conflict**:
   * Removed pipe (`|`) characters inside text strings (replaced with `-`), preventing frame field corruption during 11-field pipe splitting.
4. **Activity Lifecycle Integration**:
   * Shifted BLE initialization to `onStart()` / `onDestroy()` in `MainActivity.kt`, eliminating accidental BLE disconnects caused by Compose recompositions.

### C. Incident Command Base Station (`command_center/app.py`)
1. **Auto-Detect USB Serial Port**: Automatically connects to macOS `/dev/cu.usbserial*` or `/dev/cu.wchusbserial*` at `115200 baud`.
2. **Database Auto-Migration**: Added SQLite column auto-migration for `path` in `resqmesh_disaster.db`.

---

## 🛠️ 4. ESP32 Hardware Wiring & Breadboard Setup Guide

### Parts Needed:
* **3 x ESP32 Development Boards** (Node32s, ESP32-WROOM-32, or ESP32-S3)
* **3 x USB-A to Micro-USB / USB-C Cables**
* *(Optional)* Breadboard + Power Bank / Battery Pack for field nodes

### Hardware Setup Instructions:
1. **Node 1 (Command Gateway `RQ50EE`)**:
   * Plug directly into the Incident Command Laptop via USB cable.
   * *No breadboard wiring required.* The USB cable powers the board and provides the 115200 baud serial connection to `command_center/app.py`.
2. **Node 2 & Node 3 (Field Nodes `RQA0C2`, `RQ9CDF`)**:
   * Power using any USB port, phone charger, or 5V power bank connected to the `VIN` / `5V` and `GND` pins on a breadboard.
   * *No external sensors or wiring required.* The ESP32's onboard Wi-Fi and Bluetooth antennas handle all radio and mobile communications.

---

## 💻 5. Laptop & Software Environment Setup

Have your friend install the following tools on their laptop:

### Required Software:
1. **Arduino IDE 2.x**:
   * Download from: [https://www.arduino.cc/en/software](https://www.arduino.cc/en/software)
   * Install the ESP32 Board Package:
     1. Open Arduino IDE $ightarrow$ **Settings / Preferences**.
     2. Add Additional Boards Manager URL: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
     3. Go to **Tools $ightarrow$ Board $ightarrow$ Boards Manager**, search `esp32` by Espressif, and click **Install**.
2. **Python 3.10+**:
   * Install Flask and PySerial:
     ```bash
     pip3 install flask pyserial
     ```
3. **Android Studio (2024.1+) & Android SDK 34**:
   * Required to build and deploy the Android app.

---

## 📂 6. Important Project File Paths

| Component | Absolute Path |
| :--- | :--- |
| **Project Root Folder** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2` |
| **ESP32 Firmware** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/firmware/ResQMesh_ESP32_Node/ResQMesh_ESP32_Node.ino` |
| **Command Center Server** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/command_center/app.py` |
| **BLE Manager (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ble/BleManager.kt` |
| **ViewModel (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/viewmodel/TacticalViewModel.kt` |
| **OpenStreetMap View** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ui/components/OsmMapView.kt` |
| **Emergency Feed Log** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ui/components/EmergencyFeed.kt` |

---

## ⚡ 7. Step-by-Step Operational & Testing Protocol

### Step A: Flashing the ESP32 Boards in Arduino IDE
1. Open Arduino IDE and select **File $ightarrow$ Open**:
   `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/firmware/ResQMesh_ESP32_Node/ResQMesh_ESP32_Node.ino`
2. Connect your ESP32 board via USB.
3. Select board model: **Tools $ightarrow$ Board $ightarrow$ ESP32 Dev Module**.
4. **CRITICAL SETTING**: Go to **Tools $ightarrow$ Partition Scheme** and change it to **`Huge APP (3MB No OTA/1MB SPIFFS)`**.
5. Select port (**Tools $ightarrow$ Port**) and click **Upload** (⚡ arrow icon).
6. Repeat for all 3 ESP32 boards.

### Step B: Launching the Command Center Laptop Base Station
1. Plug **Node 1** into the laptop via USB cable.
2. Open terminal and run:
   ```bash
   python3 command_center/app.py
   ```
3. Open browser at: **`http://localhost:5050`**

### Step C: Mobile Phones Testing
1. Deploy the Android app to **Phone 1** and **Phone 2**.
2. **Phone 1** auto-connects over BLE to **Node 2** (`ResQMesh_RQA0C2`).
3. **Phone 2** auto-connects over BLE to **Node 3** (`ResQMesh_RQ9CDF`).
4. Tap **`🚑 MEDICAL`** on Phone 1:
   * Message hops: `Phone 1 ➔ Node 2 ➔ Node 3 ➔ Phone 2` AND `Node 2 ➔ Node 1 ➔ Laptop Base Station`.
   * **Phone 2** displays the SOS message with GPS coordinates and a **`🎯 LOCK TARGET`** button.
   * Tapping **`🎯 LOCK TARGET`** centers OpenStreetMap on Phone 1's position, draws the red navigation line, and points the vector compass arrow directly toward Phone 1!
