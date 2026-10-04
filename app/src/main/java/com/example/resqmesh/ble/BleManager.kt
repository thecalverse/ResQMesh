package com.example.resqmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.resqmesh.model.ResQFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class DiscoveredNode(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
    val lastSeen: Long = System.currentTimeMillis()
)

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "ResQMesh_BLE"

        // Nordic UART Service (NUS)
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _currentRssi = MutableStateFlow(-100)
    val currentRssi: StateFlow<Int> = _currentRssi.asStateFlow()

    private val _discoveredNodesMap = mutableMapOf<String, DiscoveredNode>()
    private val failedNodeCooldownMap = mutableMapOf<String, Long>()
    private val _discoveredNodes = MutableStateFlow<List<DiscoveredNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredNode>> = _discoveredNodes.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<ResQFrame>(extraBufferCapacity = 512)
    val incomingFrames: SharedFlow<ResQFrame> = _incomingFrames.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var rssiPollJob: Job? = null
    private val incomingBuffer = StringBuilder()

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun promptEnableBluetooth() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(enableBtIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error prompting Bluetooth enable: ${e.message}")
            }
        }
    }

    private fun parseDeviceNameFromBytes(bytes: ByteArray?): String? {
        if (bytes == null) return null
        var i = 0
        while (i < bytes.size) {
            val length = bytes[i].toInt() and 0xFF
            if (length == 0) break
            if (i + length >= bytes.size) break
            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                val nameBytes = bytes.copyOfRange(i + 2, i + 1 + length)
                return String(nameBytes, StandardCharsets.UTF_8).trim()
            }
            i += length + 1
        }
        return null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val rawBytes = result.scanRecord?.bytes
            val parsedNameFromBytes = parseDeviceNameFromBytes(rawBytes)
            val scanRecordName = result.scanRecord?.deviceName
            val deviceName = parsedNameFromBytes ?: scanRecordName ?: device.name ?: ""

            val serviceUuids = result.scanRecord?.serviceUuids
            val hasNusUuid = serviceUuids?.any { it.uuid == NUS_SERVICE_UUID } == true

            val isMatch = deviceName.startsWith("RQ") ||
                    deviceName.contains("ResQMesh") ||
                    hasNusUuid ||
                    device.address.startsWith("B0:3F:D3", ignoreCase = true) ||
                    (rawBytes != null && String(rawBytes, StandardCharsets.UTF_8).contains("ResQMesh"))

            if (isMatch) {
                val displayName = if (deviceName.isNotBlank()) deviceName else "ResQMesh_${device.address.takeLast(4)}"
                val rssi = result.rssi

                val discoveredNode = DiscoveredNode(
                    name = displayName,
                    address = device.address,
                    rssi = rssi,
                    device = device
                )

                synchronized(_discoveredNodesMap) {
                    _discoveredNodesMap[device.address] = discoveredNode
                    _discoveredNodes.value = _discoveredNodesMap.values.sortedByDescending { it.rssi }
                }

                Log.i(TAG, "⚡ Discovered ResQMesh BLE node: $displayName (${device.address}) RSSI: $rssi dBm")

                // Auto-connect to closest available (non-busy/failed) node
                if (_connectionState.value == BleConnectionState.SCANNING || _connectionState.value == BleConnectionState.DISCONNECTED) {
                    val now = System.currentTimeMillis()
                    val bestNode = _discoveredNodes.value.firstOrNull { node ->
                        (failedNodeCooldownMap[node.address] ?: 0L) < now
                    }
                    if (bestNode != null) {
                        Log.i(TAG, "⚡ AUTO-CONNECTING to BLE node: ${bestNode.name} (${bestNode.address}) RSSI: ${bestNode.rssi} dBm")
                        stopScan()
                        connectToDevice(bestNode.device)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code: $errorCode")
            _connectionState.value = BleConnectionState.DISCONNECTED
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                stopScan()
                Handler(Looper.getMainLooper()).postDelayed({
                    startAutoScan()
                }, 500)
            }
        }
    }

    fun startAutoScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }

        if (_connectionState.value == BleConnectionState.CONNECTED || _connectionState.value == BleConnectionState.CONNECTING) {
            return
        }

        stopScan() // Safely stop any running scan first to avoid SCAN_FAILED_ALREADY_STARTED
        _connectionState.value = BleConnectionState.SCANNING
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "⚡ Active low-latency BLE scan searching for physical ResQMesh nodes...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan: ${e.message}")
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    fun connectToSpecificDevice(address: String) {
        val targetNode = _discoveredNodesMap[address]
        if (targetNode != null) {
            stopScan()
            connectToDevice(targetNode.device)
        } else {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                stopScan()
                connectToDevice(device)
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        _connectedDeviceName.value = device.name ?: "ResQMesh_${device.address.takeLast(4)}"

        Log.d(TAG, "Connecting GATT to ${device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "⚡ GATT Connected successfully! Queueing discoverServices...")
                Handler(Looper.getMainLooper()).postDelayed({
                    val ok = gatt?.discoverServices() ?: false
                    Log.d(TAG, "⚡ Initiated gatt.discoverServices(): $ok")
                }, 250)
                startRssiPolling()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val address = gatt?.device?.address ?: ""
                if (address.isNotBlank()) {
                    // Fast 3-second cooldown so phone quickly pairs with available nodes
                    failedNodeCooldownMap[address] = System.currentTimeMillis() + 3000L
                }
                Log.w(TAG, "BLE Node Disconnected ($address, status=$status)! Re-scanning immediately for separate available nodes...")
                _connectionState.value = BleConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                stopRssiPolling()
                try {
                    gatt?.close()
                } catch (_: Exception) {}
                bluetoothGatt = null

                Handler(Looper.getMainLooper()).postDelayed({
                    startAutoScan()
                }, 400)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "⚡ GATT MTU negotiated: $mtu (status=$status)")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d(TAG, "⚡ onServicesDiscovered status=$status, total services=${gatt?.services?.size}")
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                try {
                    gatt.requestMtu(512)
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting MTU: ${e.message}")
                }

                var foundRx: BluetoothGattCharacteristic? = null
                var foundTx: BluetoothGattCharacteristic? = null

                // Priority 1: Check exact NUS Service UUID
                val nusService = gatt.getService(NUS_SERVICE_UUID)
                if (nusService != null) {
                    foundRx = nusService.getCharacteristic(NUS_RX_CHAR_UUID)
                    foundTx = nusService.getCharacteristic(NUS_TX_CHAR_UUID)
                }

                // Priority 2: Fallback search across non-generic services
                if (foundRx == null || foundTx == null) {
                    for (service in gatt.services) {
                        val sUuid = service.uuid.toString().lowercase(java.util.Locale.ROOT)
                        if (sUuid.startsWith("00001800") || sUuid.startsWith("00001801") || sUuid.startsWith("0000180a")) {
                            continue
                        }

                        for (ch in service.characteristics) {
                            val cUuid = ch.uuid.toString().lowercase(java.util.Locale.ROOT)
                            val props = ch.properties
                            val isWritable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                            val isNotifiable = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                                    (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                            if (cUuid.contains("6e400002")) {
                                foundRx = ch
                            } else if (foundRx == null && isWritable) {
                                foundRx = ch
                            }

                            if (cUuid.contains("6e400003")) {
                                foundTx = ch
                            } else if (foundTx == null && isNotifiable) {
                                foundTx = ch
                            }
                        }
                    }
                }

                rxCharacteristic = foundRx
                txCharacteristic = foundTx
                Log.i(TAG, "⚡ Linked RX Char: ${foundRx?.uuid}, TX Char: ${foundTx?.uuid}")

                if (rxCharacteristic != null) {
                    _connectionState.value = BleConnectionState.CONNECTED
                    Log.i(TAG, "⚡ BLE Service active & Linked! Connected to ${gatt.device.name}")
                } else {
                    Log.e(TAG, "Failed to locate writable BLE characteristic on ${gatt.device.address}")
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }

                // Sequence MTU request and Descriptor 0x2902 write to prevent GATT_BUSY collision
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        gatt.requestMtu(247)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting MTU: ${e.message}")
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        enableNotifications(gatt, foundTx)
                    }, 350)
                }, 150)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            Log.i(TAG, "⚡ [GATT CONFIRMED] Descriptor Write status=$status for ${descriptor?.uuid}")
            if (status != BluetoothGatt.GATT_SUCCESS && descriptor != null && gatt != null) {
                Log.w(TAG, "Descriptor 0x2902 write pending retry due to status $status...")
                Handler(Looper.getMainLooper()).postDelayed({
                    enableNotifications(gatt, txCharacteristic)
                }, 400)
            }
        }

        private fun enableNotifications(gatt: BluetoothGatt, tx: BluetoothGattCharacteristic?) {
            if (tx == null) return
            try {
                val setOk = gatt.setCharacteristicNotification(tx, true)
                Log.i(TAG, "⚡ setCharacteristicNotification result: $setOk")

                val descriptor = tx.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    ?: tx.descriptors.firstOrNull()

                if (descriptor != null) {
                    val enableVal = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val res = gatt.writeDescriptor(descriptor, enableVal)
                        Log.i(TAG, "⚡ writeDescriptor 0x2902 result code: $res")
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = enableVal
                        @Suppress("DEPRECATION")
                        val ok = gatt.writeDescriptor(descriptor)
                        Log.i(TAG, "⚡ writeDescriptor 0x2902 result legacy: $ok")
                    }
                } else {
                    Log.e(TAG, "Descriptor 0x2902 missing on TX characteristic!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in enableNotifications: ${e.message}")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value ?: return
            val textChunk = String(value, StandardCharsets.UTF_8)
            handleReceivedChunk(textChunk)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val textChunk = String(value, StandardCharsets.UTF_8)
            handleReceivedChunk(textChunk)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _currentRssi.value = rssi
            }
        }
    }

    private fun handleReceivedChunk(chunk: String) {
        synchronized(incomingBuffer) {
            incomingBuffer.append(chunk)
            val fullStr = incomingBuffer.toString().replace("\r\n", "\n").replace("\r", "\n")

            if (fullStr.contains("\n")) {
                val lines = fullStr.split("\n")
                for (i in 0 until lines.size - 1) {
                    val line = lines[i].trim()
                    if (line.isNotEmpty()) {
                        Log.i(TAG, "⚡ BLE Frame Received from Node: $line")
                        val frame = ResQFrame.parse(line, _currentRssi.value)
                        if (frame != null) {
                            Log.i(TAG, "⚡ Parsed ResQFrame: mtype=${frame.mtype}, src=${frame.srcId}, text=${frame.text}")
                            scope.launch {
                                _incomingFrames.emit(frame)
                            }
                        } else {
                            Log.w(TAG, "Failed to parse BLE line: '$line'")
                        }
                    }
                }
                incomingBuffer.clear()
                incomingBuffer.append(lines.last())
            } else {
                if (incomingBuffer.length > 1024) {
                    incomingBuffer.clear()
                }
            }
        }
    }

    fun sendFrame(frame: ResQFrame): Boolean {
        val payload = frame.toMeshString() + "\n"
        return sendRawData(payload)
    }

    fun sendRawData(payload: String): Boolean {
        val gatt = bluetoothGatt
        val rx = rxCharacteristic
        if (gatt == null || rx == null) {
            Log.e(TAG, "Cannot send: BLE not connected or writable characteristic missing (gatt=$gatt, rx=$rx, state=${_connectionState.value})")
            return false
        }

        val formattedPayload = if (!payload.endsWith("\n")) payload + "\n" else payload
        val bytes = formattedPayload.toByteArray(StandardCharsets.UTF_8)

        val maxChunkSize = 20
        var totalSuccess = true
        var offset = 0

        while (offset < bytes.size) {
            val length = minOf(maxChunkSize, bytes.size - offset)
            val chunk = bytes.copyOfRange(offset, offset + length)

            var success = false

            // Try WRITE_TYPE_NO_RESPONSE first
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(rx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                success = (res == BluetoothStatusCodes.SUCCESS)
            } else {
                @Suppress("DEPRECATION")
                rx.value = chunk
                @Suppress("DEPRECATION")
                success = gatt.writeCharacteristic(rx)
            }

            // Fallback to WRITE_TYPE_DEFAULT if NO_RESPONSE fails on device vendor stack
            if (!success) {
                rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeCharacteristic(rx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    success = (res == BluetoothStatusCodes.SUCCESS)
                } else {
                    @Suppress("DEPRECATION")
                    rx.value = chunk
                    @Suppress("DEPRECATION")
                    success = gatt.writeCharacteristic(rx)
                }
            }

            if (!success) {
                totalSuccess = false
                Log.e(TAG, "Failed to write BLE chunk offset $offset (bytes=${bytes.size})")
            }
            offset += length
            if (offset < bytes.size) {
                Thread.sleep(25) // 25ms pause for Bluetooth controller queue stability
            }
        }

        Log.i(TAG, "⚡ [BLE TX WRITE] Sent payload (${formattedPayload.trim()}), totalSuccess: $totalSuccess")
        return totalSuccess
    }

    private fun startRssiPolling() {
        rssiPollJob?.cancel()
        rssiPollJob = scope.launch {
            while (isActive) {
                try {
                    bluetoothGatt?.readRemoteRssi()
                } catch (_: Exception) {}
                delay(1500)
            }
        }
    }

    private fun stopRssiPolling() {
        rssiPollJob?.cancel()
        rssiPollJob = null
    }

    fun disconnect() {
        stopRssiPolling()
        stopScan()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT: ${e.message}")
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }
}
