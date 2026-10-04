@echo off
title ResQMesh Incident Command Base Station Launcher
color 0A
echo ========================================================
echo   RESQMESH INCIDENT COMMAND BASE STATION LAUNCHER
echo ========================================================
echo.
echo 1. Installing required Python packages (flask, pyserial)...
pip install flask pyserial
echo.
echo 2. Starting Base Station Web Dashboard on http://localhost:5050...
echo    Tethered Gateway USB Port auto-detection active!
echo.
python app.py
pause
