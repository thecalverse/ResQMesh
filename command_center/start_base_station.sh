#!/bin/bash
echo "========================================================"
echo "  RESQMESH INCIDENT COMMAND BASE STATION LAUNCHER"
echo "========================================================"
echo ""
echo "1. Installing required Python packages..."
pip3 install flask pyserial
echo ""
echo "2. Starting Base Station Web Dashboard on http://localhost:5050..."
python3 app.py
