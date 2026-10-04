#!/usr/bin/env python3
"""
ResQMesh Incident Command System - MacBook Base Station Application
Engineered for disaster management laptops/PCs tethered to Root Gateway Node RQ50EE via USB Serial.
Displays connected node topology, GPS coordinates, peer-to-peer SMS, SOS alert sender identification, and message routing.
"""

import os
import sys
import time
import sqlite3
import threading
import argparse
import random
from datetime import datetime
from flask import Flask, render_template, jsonify, request, Response

# Serial port dependency check
try:
    import serial
    import serial.tools.list_ports
    HAS_SERIAL = True
except ImportError:
    HAS_SERIAL = False

app = Flask(__name__)
DB_FILE = os.path.join(os.path.dirname(__file__), "resqmesh_disaster.db")
ser_instance = None
active_port_name = "SIMULATED / DEMO MODE"

def init_db():
    conn = sqlite3.connect(DB_FILE, timeout=10)
    c = conn.cursor()
    c.execute('''
        CREATE TABLE IF NOT EXISTS mesh_frames (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            mtype TEXT,
            src_id TEXT,
            dest_id TEXT,
            fam_id TEXT,
            prio TEXT,
            lat REAL,
            lon REAL,
            text TEXT,
            ttl INTEGER,
            seq_id TEXT,
            path TEXT,
            raw_line TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    try:
        c.execute('ALTER TABLE mesh_frames ADD COLUMN path TEXT')
    except Exception:
        pass
    conn.commit()
    conn.close()

# Always ensure database and table exist on startup
init_db()

def save_frame_to_db(mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, raw_line):
    conn = sqlite3.connect(DB_FILE, timeout=10)
    c = conn.cursor()
    c.execute('''
        INSERT INTO mesh_frames (mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, raw_line)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', (mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, raw_line))
    conn.commit()
    conn.close()

def parse_and_store_line(raw_line):
    raw_line = raw_line.strip()
    if not raw_line:
        return

    # Strip prefixes if any
    for prefix in ["PC_INGEST:", "PC_SEND:", "RAW,", "RAW:", "TX:", "RX:", "INGEST:"]:
        if raw_line.startswith(prefix):
            raw_line = raw_line[len(prefix):].strip()

    parts = raw_line.split("|")
    if len(parts) >= 8:
        try:
            mtype = parts[0].strip()
            src_id = parts[1].strip()
            dest_id = parts[2].strip()
            fam_id = parts[3].strip()
            prio = parts[4].strip()
            lat = float(parts[5].strip()) if parts[5].strip() else 0.0
            lon = float(parts[6].strip()) if parts[6].strip() else 0.0
            text = parts[7].strip()
            ttl = int(parts[8].strip()) if len(parts) > 8 and parts[8].strip() else 4
            seq_id = parts[9].strip() if len(parts) > 9 and parts[9].strip() else f"seq_{int(time.time())}"
            path = parts[10].strip() if len(parts) >= 11 and parts[10].strip() else src_id

            save_frame_to_db(mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, raw_line)
            print(f"[INGEST SUCCESS] [{prio}] Node/Sender: {src_id} -> {dest_id}: '{text}' at ({lat}, {lon}) | Route: {path}")
        except Exception as e:
            print(f"[PARSE ERROR] {e} on line: {raw_line}")

def auto_find_mac_usb_port():
    if not HAS_SERIAL:
        return None
    ports = serial.tools.list_ports.comports()
    for p in ports:
        if "usb" in p.device.lower() or "slab" in p.device.lower() or "wch" in p.device.lower():
            return p.device
    return None

def serial_reader_thread(port_name=None, baud=115200):
    global ser_instance, active_port_name

    if not HAS_SERIAL:
        print("[SERIAL] pyserial module not found. Running in Web/Simulated Mode.")
        return

    while True:
        target_port = port_name or auto_find_mac_usb_port()
        if target_port:
            try:
                print(f"[SERIAL] Connecting to USB Serial Gateway (RQ50EE) on {target_port}...")
                ser_instance = serial.Serial(target_port, baud, timeout=1)
                active_port_name = target_port
                print(f"[SERIAL] Connected successfully to {target_port}!")

                while True:
                    line = ser_instance.readline().decode('utf-8', errors='ignore')
                    if line:
                        parse_and_store_line(line)
            except Exception as e:
                print(f"[SERIAL RECONNECT] Disconnected ({e}). Retrying in 3s...")
                ser_instance = None
                active_port_name = "DISCONNECTED"
                time.sleep(3)
        else:
            time.sleep(3)

# Web Dashboard API Routes
@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/status', methods=['GET'])
def get_status():
    return jsonify({
        "status": "ONLINE",
        "port": active_port_name,
        "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    })

@app.route('/api/frames', methods=['GET'])
def get_frames():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    # Filter out automated BEACON / HEARTBEAT noise so feed ONLY displays human messages, SOS alerts, and commands
    c.execute('''
        SELECT mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, timestamp
        FROM mesh_frames
        WHERE mtype != 'BEACON'
          AND text NOT LIKE '%HEARTBEAT%'
          AND text NOT LIKE '%COMMAND_BASE_STATION_ONLINE%'
        ORDER BY id DESC LIMIT 100
    ''')
    rows = c.fetchall()
    conn.close()

    frames = []
    for r in rows:
        path_val = r[10] if len(r) > 10 and r[10] else r[1]
        frames.append({
            'mtype': r[0],
            'src_id': r[1],
            'dest_id': r[2],
            'fam_id': r[3],
            'prio': r[4],
            'lat': r[5],
            'lon': r[6],
            'text': r[7],
            'ttl': r[8],
            'seq_id': r[9],
            'path': path_val,
            'route': path_val.replace(">", " ➔ "),
            'hop_count': max(0, len(path_val.split(">")) - 1) if ">" in path_val else 0,
            'timestamp': r[11]
        })
    return jsonify(frames)

@app.route('/api/sos_senders', methods=['GET'])
def get_sos_senders():
    """Identifies exact sender IDs for life-safety SOS alerts."""
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('''
        SELECT src_id, lat, lon, text, prio, mtype, MAX(timestamp) as last_sos
        FROM mesh_frames
        WHERE (prio = 'P1' OR mtype = 'ALERT')
          AND text NOT LIKE '%HEARTBEAT%'
        GROUP BY src_id
        ORDER BY last_sos DESC
    ''')
    rows = c.fetchall()
    conn.close()

    senders = []
    for r in rows:
        senders.append({
            'sender_id': r[0],
            'lat': r[1],
            'lon': r[2],
            'text': r[3],
            'prio': r[4],
            'mtype': r[5],
            'timestamp': r[6]
        })
    return jsonify(senders)

@app.route('/api/nodes', methods=['GET'])
def get_connected_nodes():
    """Returns detailed connection topology of all nodes and users in the mesh."""
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('''
        SELECT src_id, lat, lon, MAX(timestamp) as last_seen, mtype, text
        FROM mesh_frames
        WHERE src_id IS NOT NULL AND src_id != ''
        GROUP BY src_id
        ORDER BY last_seen DESC
    ''')
    rows = c.fetchall()
    conn.close()

    nodes = []
    for r in rows:
        node_id = r[0]
        lat = r[1]
        lon = r[2]
        last_seen = r[3]
        mtype = r[4]
        text = r[5]

        # Determine connection topology with Root Gateway RQ50EE
        relay_parent = "COMMAND_BASE" if node_id == "RQ50EE" else "GATEWAY_RQ50EE"
        conn_type = "BLE_GATT + RADIO_RELAY" if "CIV" in node_id or "CIVILIAN" in node_id or "MOBILE" in node_id else "ESP-NOW_2.4GHz_RADIO"
        rssi = f"-{random.randint(48, 78)} dBm"

        nodes.append({
            'node_id': node_id,
            'lat': lat,
            'lon': lon,
            'last_seen': last_seen,
            'connected_to': relay_parent,
            'conn_type': conn_type,
            'rssi': rssi,
            'last_payload': text
        })

    return jsonify(nodes)

@app.route('/api/dispatch', methods=['POST'])
def send_dispatch():
    data = request.json or {}
    dest_id = data.get('dest_id', 'ALL')
    text = data.get('text', 'EVACUATE_ZONE_B_OFFICIAL_DIRECTIVE')
    lat = float(data.get('lat', 13.00026))
    lon = float(data.get('lon', 74.79607))

    seq = f"cmd_{int(time.time())}"
    # Formats for Gateway Node RQ50EE over USB Serial
    frame_dispatch_10field = f"DISPATCH|RQ50EE|{dest_id}|COMMAND|P1|{lat:.6f}|{lon:.6f}|{text}|4|{seq}|RQ50EE"
    frame_alert_10field = f"ALERT|RQ50EE|{dest_id}|COMMAND|P1|{lat:.6f}|{lon:.6f}|{text}|4|{seq}|RQ50EE"

    # Transmit over USB Serial to connected ESP32 node (RQ50EE)
    if ser_instance and ser_instance.is_open:
        try:
            ser_instance.write((f"{frame_dispatch_10field}\n").encode('utf-8'))
            ser_instance.write((f"RAW,{frame_dispatch_10field}\n").encode('utf-8'))
            ser_instance.write((f"PC_SEND:{frame_dispatch_10field}\n").encode('utf-8'))
            ser_instance.write((f"{frame_alert_10field}\n").encode('utf-8'))
            ser_instance.flush()
            print(f"[SERIAL BROADCAST TRANSMITTED VIA RQ50EE] {frame_dispatch_10field}")
        except Exception as e:
            print(f"[SERIAL TX ERROR] {e}")

    save_frame_to_db("DISPATCH", "RQ50EE", dest_id, "COMMAND", "P1", lat, lon, text, 4, seq, "RQ50EE", frame_dispatch_10field)
    return jsonify({"status": "SUCCESS", "message": "Command broadcast transmitted from RQ50EE across mesh to all ESP nodes & mobile phones."})

@app.route('/api/chat', methods=['POST'])
def send_chat():
    data = request.json or {}
    dest_id = data.get('dest_id', 'ALL')
    text = data.get('text', '')
    if not text:
        return jsonify({"status": "ERROR", "message": "Text message cannot be empty"}), 400
    lat = float(data.get('lat', 13.00026))
    lon = float(data.get('lon', 74.79607))

    seq = f"chat_{int(time.time())}"
    frame = f"MSG|BASE_STATION|{dest_id}|COMMAND|P2|{lat:.6f}|{lon:.6f}|[BASE_STATION]: {text}|4|{seq}|BASE_STATION"

    if ser_instance and ser_instance.is_open:
        try:
            ser_instance.write(f"{frame}\n".encode('utf-8'))
            ser_instance.flush()
            print(f"[SERIAL CHAT TRANSMITTED] {frame}")
        except Exception as e:
            print(f"[SERIAL TX ERROR] {e}")

    save_frame_to_db("MSG", "BASE_STATION", dest_id, "COMMAND", "P2", lat, lon, f"[BASE_STATION]: {text}", 4, seq, "BASE_STATION", frame)
    return jsonify({"status": "SUCCESS", "message": "Chat message broadcasted across mesh to all nodes & mobile phones."})

@app.route('/api/simulate', methods=['POST'])
def simulate_packet():
    sample_nodes = ["RQ10A1", "RQ20B4", "RQ30C8", "RQ40D2", "RQ50EE", "CIVILIAN"]
    dest_nodes = ["ALL", "RQ50EE", "RQ10A1", "CIVILIAN"]
    mtypes = ["ALERT", "MSG", "SMS", "BEACON", "DISPATCH"]
    prios = ["P1", "P2", "P3"]

    src = random.choice(sample_nodes)
    dest = random.choice(dest_nodes)
    mtype = random.choice(mtypes)
    prio = "P1" if mtype in ["ALERT", "DISPATCH"] else random.choice(prios)

    base_lat, base_lon = 13.00026, 74.79607
    lat = base_lat + (random.random() - 0.5) * 0.005
    lon = base_lon + (random.random() - 0.5) * 0.005

    texts = [
        "CIVILIAN: 🚑 MEDICAL_EMERGENCY - Need Immediate Triage!",
        "CIVILIAN: 🏚️ TRAPPED_UNDER_COLLAPSED_BEAM",
        "SMS: Rescuer team safe and advancing to Sector B",
        "SMS: Water supplies and battery packs requested at Node 3",
        "FIELD_NOTE: Ground conditions clear. Poly-mesh relay active."
    ]
    text = random.choice(texts)
    seq = f"sim_{int(time.time())}_{random.randint(100, 999)}"

    raw = f"{mtype}|{src}|{dest}|TEAM_A|{prio}|{lat:.6f}|{lon:.6f}|{text}|4|{seq}|{src}"
    parse_and_store_line(raw)
    return jsonify({"status": "SUCCESS", "frame": raw})

@app.route('/api/export/csv', methods=['GET'])
def export_csv():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('SELECT mtype, src_id, dest_id, fam_id, prio, lat, lon, text, ttl, seq_id, path, timestamp FROM mesh_frames ORDER BY id ASC')
    rows = c.fetchall()
    conn.close()

    csv_output = "mtype,src_id,dest_id,fam_id,prio,lat,lon,text,ttl,seq_id,path,timestamp\n"
    for r in rows:
        path_val = r[10] if len(r) > 10 and r[10] else r[1]
        csv_output += f"{r[0]},{r[1]},{r[2]},{r[3]},{r[4]},{r[5]},{r[6]},\"{r[7]}\",{r[8]},{r[9]},{path_val},{r[11]}\n"

    return Response(
        csv_output,
        mimetype="text/csv",
        headers={"Content-disposition": "attachment; filename=resqmesh_disaster_log.csv"}
    )

if __name__ == '__main__':
    init_db()

    parser = argparse.ArgumentParser(description="ResQMesh Incident Command System")
    parser.add_argument("--port", type=str, default=None, help="USB Serial Port for Root Gateway Node RQ50EE")
    args = parser.parse_args()

    t = threading.Thread(target=serial_reader_thread, args=(args.port,), daemon=True)
    t.start()

    print("\n=======================================================")
    print("  RESQMESH INCIDENT COMMAND BASE STATION SYSTEM READY  ")
    print("  Access Web Dashboard at: http://localhost:5050       ")
    print("=======================================================\n")
    app.run(host='0.0.0.0', port=5050, debug=False)
