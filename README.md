# 🛰️ ResQMesh: Complete Off-Grid Disaster Mesh Communications System
### Master Handoff, Hardware Setup, Firmware Flashing & Technical Instruction Manual

---

## 📖 1. Executive Summary & Vision
**ResQMesh** is a zero-infrastructure, off-grid disaster communications platform engineered for emergency rescue scenarios where cellular towers, Wi-Fi routers, and internet backbones have failed (earthquakes, floods, network blackouts). 

The system forms an **autonomous, self-healing 2.4 GHz radio mesh network** using low-cost ESP32 microcontrollers, mobile Android smartphones (over BLE GATT), and an Incident Command Base Station laptop tethered via USB Serial.

### Core Goals:
* Allow **Civilians** to send life-safety SOS alerts, GPS coordinates, and messages directly from their phones.
* Have **ESP32 Nodes** hop these messages across 2.4 GHz radio waves (**ESP-NOW**) from node to node without internet or Wi-Fi routers.
* Deliver the SOS alerts to **Rescuers' phones** (which automatically plot the victim's location on OpenStreetMap and point a vector compass arrow toward them) and to the **Incident Command Base Station Laptop** (running a live Web Dashboard at `http://localhost:5050`).

---

## 🏗️ 2. System Architecture & Network Topology

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

## 💻 3. Laptop & Development Environment Setup Instructions

Have your friend install the following software tools on their laptop:

### 1. Arduino IDE 2.x Installation:
* Download and install from: [https://www.arduino.cc/en/software](https://www.arduino.cc/en/software)
* Add Espressif ESP32 Board Package:
  1. Open Arduino IDE $ightarrow$ **Settings / Preferences** (`Cmd + ,` or `Ctrl + ,`).
  2. Paste this URL into **Additional Boards Manager URLs**:
     `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
  3. Go to **Tools $ightarrow$ Board $ightarrow$ Boards Manager**, search `esp32` by Espressif, and click **Install**.

### 2. Python 3.10+ Environment:
* Open terminal and install required packages:
  ```bash
  pip3 install flask pyserial
  ```

### 3. Android Studio & SDK 34:
* Download Android Studio (2024.1+ / Ladybug or newer).
* Ensure Android SDK Platform 34 and Build-Tools 34 are installed in **SDK Manager**.

---

## 🛠️ 4. Hardware Wiring & Breadboard Setup Instructions

### Hardware Parts Needed:
* **3 x ESP32 Development Boards** (Node32s, ESP32-WROOM-32, or ESP32-S3)
* **3 x USB-A to Micro-USB / USB-C Cables**
* *(Optional)* Breadboard + Power Bank / 5V Battery Pack for field nodes

### Hardware Assembly & Wiring Protocol:
1. **Node 1 (Command Gateway `RQ50EE`)**:
   * Plug directly into the Incident Command Laptop via USB cable.
   * *Wiring*: None needed. The USB cable supplies 5V power and handles 115200 baud serial communication to `command_center/app.py`.
2. **Node 2 & Node 3 (Field Mesh Nodes `RQA0C2`, `RQ9CDF`)**:
   * Insert each ESP32 board onto a breadboard.
   * Connect a 5V power supply or USB power bank to the `VIN` (5V) and `GND` pins.
   * *Wiring*: No external sensors or external antennas required. The onboard PCB trace antenna handles 2.4GHz radio broadcasts (ESP-NOW) and Bluetooth (BLE).

---

## ⚡ 5. Step-by-Step ESP32 Firmware Flashing Guide

Follow these exact steps to flash the 3 ESP32 boards in Arduino IDE:

1. Open Arduino IDE and select **File $ightarrow$ Open**:
   `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/firmware/ResQMesh_ESP32_Node/ResQMesh_ESP32_Node.ino`
2. Plug the ESP32 board into your laptop via USB cable.
3. Select the board model: **Tools $ightarrow$ Board $ightarrow$ ESP32 Arduino $ightarrow$ ESP32 Dev Module**.
4. **CRITICAL SETTING (PREVENTS "SKETCH TOO BIG" ERROR)**:
   * Go to **Tools $ightarrow$ Partition Scheme**.
   * Change it from `Default 4MB` to **`Huge APP (3MB No OTA/1MB SPIFFS)`**.
5. Select the COM / TTY serial port under **Tools $ightarrow$ Port**.
6. Click **Upload** (⚡ arrow icon).
7. Repeat the upload process for all 3 ESP32 boards.

### Troubleshooting Flashing Errors:
* **Error**: `Resource busy: /dev/cu.wchusbserial*`
  * *Fix*: Close the Python `app.py` script or terminal running on port 5050 before clicking upload in Arduino IDE so the serial port is free.
* **Error**: `Sketch uses 1644111 bytes (125%)`
  * *Fix*: Ensure **Tools $ightarrow$ Partition Scheme** is set to **`Huge APP (3MB No OTA/1MB SPIFFS)`**.

---

## 🖥️ 6. Command Center Base Station Server Instructions

1. Plug **Node 1** into your laptop via USB.
2. Open terminal and navigate to the project directory:
   ```bash
   cd /Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2
   python3 command_center/app.py
   ```
3. The server will output:
   `[SERIAL] Connected successfully to /dev/cu.usbserial-110!`
4. Open your web browser and navigate to: **`http://localhost:5050`**
5. **Dashboard Features**:
   * **Live Incident Feed**: Displays incoming SOS alerts, messages, sender IDs, and full hop paths (`CIV_2356 ➔ RQA0C2 ➔ RQ50EE`).
   * **Broadcast Dispatch**: Click **Broadcast Dispatch** on the UI to send official emergency evacuation alerts across the entire mesh network to all ESP32 nodes and mobile phones simultaneously.

---

## 📱 7. Mobile Application Operational & Testing Protocol

### Deploying the App:
1. Open the project in Android Studio.
2. Connect **Phone 1** and **Phone 2** via USB with USB Debugging enabled.
3. Build and install the app using `app:assembleDebug` or click **Run 'app'** in Android Studio.

### Testing Step-by-Step:
1. **Auto-Connection**:
   * Open ResQMesh on **Phone 1** $ightarrow$ Auto-connects over BLE to **Node 2** (`ResQMesh_RQA0C2`).
   * Open ResQMesh on **Phone 2** $ightarrow$ Auto-connects over BLE to **Node 3** (`ResQMesh_RQ9CDF`).
2. **Sending an SOS Alert**:
   * On Phone 1, tap **`🚑 MEDICAL`** or **`🏚️ TRAPPED`**.
   * Message travels: `Phone 1 ➔ Node 2 ➔ Node 3 ➔ Phone 2` AND `Node 2 ➔ Node 1 ➔ Laptop Dashboard`.
3. **Receiving & Tracking Target on Map**:
   * **Phone 2** receives the SOS alert in its **Emergency Feed**.
   * The feed card displays:
     * **Sender ID**: `CIV_2356_775`
     * **Message**: `CIV_2356_775 (26y) - MEDICAL_EMERGENCY`
     * **GPS Location**: `📍 GPS: 13.000038, 74.796102 (32m away)`
     * **`🎯 LOCK TARGET`** button.
   * Tapping **`🎯 LOCK TARGET`**:
     * Centers OpenStreetMap on Phone 1's position and draws a **Red Navigation Line**.
     * Points the **Vector Compass Arrow** directly toward Phone 1 and displays live distance in meters.

---

## 📂 8. Absolute File Paths Reference Table

| Component | Absolute Path |
| :--- | :--- |
| **Project Root Directory** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2` |
| **Handoff Text File** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/HANDOFF_GUIDE.txt` |
| **Project README File** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/README.md` |
| **ESP32 Firmware (.ino)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/firmware/ResQMesh_ESP32_Node/ResQMesh_ESP32_Node.ino` |
| **Command Center Server (.py)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/command_center/app.py` |
| **BLE Manager (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ble/BleManager.kt` |
| **ViewModel (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/viewmodel/TacticalViewModel.kt` |
| **OpenStreetMap View (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ui/components/OsmMapView.kt` |
| **Emergency Feed Log (Android)** | `/Users/maddhamsolanki/AndroidStudioProjects/ResQMesh2/app/src/main/java/com/example/resqmesh/ui/components/EmergencyFeed.kt` |
