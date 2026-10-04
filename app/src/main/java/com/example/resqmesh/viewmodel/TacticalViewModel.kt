package com.example.resqmesh.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.resqmesh.ai.GeminiTriageEngine
import com.example.resqmesh.ai.TriageAnalysis
import com.example.resqmesh.ble.BleConnectionState
import com.example.resqmesh.ble.BleManager
import com.example.resqmesh.ble.DiscoveredNode
import com.example.resqmesh.model.MessageType
import com.example.resqmesh.model.Priority
import com.example.resqmesh.model.ResQFrame
import com.example.resqmesh.sensor.LocationAndCompassManager
import com.example.resqmesh.sensor.TacticalLocation
import com.example.resqmesh.wifi.WifiNodeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class LockedTarget(
    val nodeId: String,
    val lat: Double,
    val lon: Double,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class TacticalUiState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val isAutoConnectEnabled: Boolean = true,
    val isGpsEnabled: Boolean = true,
    val rssi: Int = -100,
    val userLocation: TacticalLocation = TacticalLocation(),
    val deviceHeading: Float = 0f,
    val lockedTarget: LockedTarget? = null,
    val targetDistanceMeters: Double = 0.0,
    val targetBearingDegrees: Float = 0f,
    val relativeArrowAngle: Float = 0f,
    val discoveredBleNodes: List<DiscoveredNode> = emptyList(),
    val framesList: List<ResQFrame> = emptyList(),
    val activeDispatchFrame: ResQFrame? = null,
    val isTriageLoading: Boolean = false,
    val activeTriageAnalysis: TriageAnalysis? = null,
    val activeTriageFrame: ResQFrame? = null
)

class TacticalViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = BleManager(application)
    val wifiNodeManager = WifiNodeManager(application)
    val udpMeshManager = com.example.resqmesh.wifi.UdpMeshManager(application)
    val sensorManager = LocationAndCompassManager(application)
    private val triageEngine = GeminiTriageEngine()

    private val _lockedTarget = MutableStateFlow<LockedTarget?>(null)
    private val _isAutoConnectEnabled = MutableStateFlow(true)
    private val _framesList = MutableStateFlow<List<ResQFrame>>(emptyList())
    private val _activeDispatchFrame = MutableStateFlow<ResQFrame?>(null)

    private val _isTriageLoading = MutableStateFlow(false)
    private val _activeTriageAnalysis = MutableStateFlow<TriageAnalysis?>(null)
    private val _activeTriageFrame = MutableStateFlow<ResQFrame?>(null)

    val uiState: StateFlow<TacticalUiState> = combine(
        bleManager.connectionState,
        bleManager.connectedDeviceName,
        _isAutoConnectEnabled,
        sensorManager.isGpsEnabled,
        sensorManager.userLocation,
        sensorManager.deviceHeading,
        _lockedTarget,
        bleManager.discoveredNodes,
        bleManager.currentRssi,
        _framesList,
        _activeDispatchFrame,
        _isTriageLoading,
        _activeTriageAnalysis
    ) { arrayOfStates ->
        val bleConnState = arrayOfStates[0] as BleConnectionState
        val deviceName = arrayOfStates[1] as String?
        val autoConnect = arrayOfStates[2] as Boolean
        val gpsEnabled = arrayOfStates[3] as Boolean
        val uLoc = arrayOfStates[4] as TacticalLocation
        val heading = arrayOfStates[5] as Float
        val target = arrayOfStates[6] as LockedTarget?
        @Suppress("UNCHECKED_CAST")
        val bleNodesList = arrayOfStates[7] as List<DiscoveredNode>
        val rssiVal = arrayOfStates[8] as Int
        @Suppress("UNCHECKED_CAST")
        val frames = arrayOfStates[9] as List<ResQFrame>
        val dispatch = arrayOfStates[10] as ResQFrame?
        val triageLoading = arrayOfStates[11] as Boolean
        val triageAnalysis = arrayOfStates[12] as TriageAnalysis?

        var dist = 0.0
        var bearing = 0f
        var arrowAngle = 0f

        if (target != null && uLoc.latitude != 0.0 && uLoc.longitude != 0.0) {
            dist = sensorManager.calculateDistanceMeters(
                uLoc.latitude, uLoc.longitude,
                target.lat, target.lon
            )
            bearing = sensorManager.calculateBearing(
                uLoc.latitude, uLoc.longitude,
                target.lat, target.lon
            )
            arrowAngle = sensorManager.calculateRelativeArrowAngle(bearing, heading)
        }

        TacticalUiState(
            connectionState = bleConnState,
            connectedDeviceName = deviceName,
            isAutoConnectEnabled = autoConnect,
            isGpsEnabled = gpsEnabled,
            rssi = rssiVal,
            userLocation = uLoc,
            deviceHeading = heading,
            lockedTarget = target,
            targetDistanceMeters = dist,
            targetBearingDegrees = bearing,
            relativeArrowAngle = arrowAngle,
            discoveredBleNodes = bleNodesList,
            framesList = frames,
            activeDispatchFrame = dispatch,
            isTriageLoading = triageLoading,
            activeTriageAnalysis = triageAnalysis,
            activeTriageFrame = _activeTriageFrame.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TacticalUiState()
    )

    init {
        startSensorsAndBle()
        viewModelScope.launch {
            bleManager.incomingFrames.collect { frame ->
                onIncomingFrameReceived(frame, source = "BLE")
            }
        }
        viewModelScope.launch {
            wifiNodeManager.incomingFrames.collect { frame ->
                onIncomingFrameReceived(frame, source = "WIFI")
            }
        }
        viewModelScope.launch {
            udpMeshManager.incomingFrames.collect { frame ->
                onIncomingFrameReceived(frame, source = "UDP")
            }
        }
    }

    private fun onIncomingFrameReceived(frame: ResQFrame, source: String = "LOCAL") {
        // Filter out automated BEACON / Heartbeat background chatter from human message feed
        if (frame.mtype == MessageType.BEACON || 
            frame.text.contains("HEARTBEAT", ignoreCase = true) || 
            frame.text.contains("COMMAND_BASE_STATION_ONLINE", ignoreCase = true)
        ) {
            return
        }

        Log.i("ResQMesh_VM", "⚡ Frame Received [Source: $source, srcId: ${frame.srcId}, text: ${frame.text}, ttl: ${frame.ttl}, path: ${frame.path}]")

        val currentList = _framesList.value.toMutableList()
        // Deduplicate based on sequence ID or srcId + text within 1.2 seconds
        val exists = currentList.any { 
            (it.seqId.isNotBlank() && it.seqId == frame.seqId) ||
            (it.srcId == frame.srcId && it.text == frame.text && (System.currentTimeMillis() - it.timestamp < 1200))
        }
        if (!exists) {
            currentList.add(0, frame)
            _framesList.value = currentList

            // Cross-protocol mesh hop relay: Bridge BLE, Wi-Fi & Direct UDP if TTL > 1
            if (frame.ttl > 1) {
                val relayedFrame = frame.copy(
                    ttl = frame.ttl - 1,
                    path = if (frame.path.isBlank()) frame.srcId else frame.path
                )
                // Only relay over BLE if message originated locally on phone or non-BLE layer (prevents BLE ping-pong echo)
                if (source != "BLE" && bleManager.connectionState.value == BleConnectionState.CONNECTED) {
                    val ok = bleManager.sendFrame(relayedFrame)
                    Log.i("ResQMesh_VM", "⚡ Relayed frame over BLE (ttl=${relayedFrame.ttl}, ok=$ok): ${relayedFrame.text}")
                }
                udpMeshManager.sendFrame(relayedFrame)
                if (wifiNodeManager.connectionState.value == com.example.resqmesh.wifi.WifiConnectionState.CONNECTED) {
                    wifiNodeManager.sendFrameOverWifi(relayedFrame)
                }
            }
        }

        if (frame.prio == Priority.P1 || frame.mtype == MessageType.DISPATCH || frame.mtype == MessageType.ALERT) {
            _activeDispatchFrame.value = frame
        }
    }

    fun connectToBleDevice(address: String) {
        bleManager.connectToSpecificDevice(address)
    }

    fun toggleAutoConnect(enabled: Boolean) {
        _isAutoConnectEnabled.value = enabled
        if (enabled) {
            startSensorsAndBle()
        } else {
            stopSensorsAndBle()
        }
    }

    fun startSensorsAndBle() {
        sensorManager.startUpdates()
        bleManager.startAutoScan()
        wifiNodeManager.startWifiConnection()
        udpMeshManager.startListening()
    }

    fun stopSensorsAndBle() {
        sensorManager.stopUpdates()
        bleManager.disconnect()
        wifiNodeManager.stopWifiConnection()
        udpMeshManager.stop()
    }

    fun lockTarget(nodeId: String, lat: Double, lon: Double) {
        _lockedTarget.value = LockedTarget(
            nodeId = nodeId,
            lat = lat,
            lon = lon
        )
    }

    fun clearTargetLock() {
        _lockedTarget.value = null
    }

    fun dismissDispatchDialog() {
        _activeDispatchFrame.value = null
    }

    fun sendQuickSos(textPreset: String, senderId: String = "CIVILIAN") {
        val uLoc = sensorManager.userLocation.value
        val deviceHash = kotlin.math.abs(senderId.hashCode()) % 100
        val microOffsetLat = (deviceHash - 50) * 0.000008
        val microOffsetLon = (deviceHash - 50) * 0.000008

        val latVal = if (uLoc.latitude != 0.0) uLoc.latitude else 13.000260 + microOffsetLat
        val lonVal = if (uLoc.longitude != 0.0) uLoc.longitude else 74.796070 + microOffsetLon

        val frame = ResQFrame(
            mtype = MessageType.ALERT,
            srcId = if (senderId.isNotBlank()) senderId else "CIVILIAN",
            destId = "ALL",
            famId = "TEAM_A",
            prio = Priority.P1,
            lat = latVal,
            lon = lonVal,
            text = textPreset,
            ttl = 4
        )
        val bleOk = bleManager.sendFrame(frame)
        wifiNodeManager.sendFrameOverWifi(frame)
        udpMeshManager.sendFrame(frame)
        onIncomingFrameReceived(frame, source = "LOCAL")
        Log.i("ResQMesh_VM", "⚡ Broadcast Outgoing Quick SOS (BLE ok: $bleOk): ${frame.toMeshString()}")
    }

    fun sendCustomMessage(text: String, destId: String = "ALL", priority: Priority = Priority.P2, senderId: String = "CIVILIAN") {
        if (text.isBlank()) return
        val uLoc = sensorManager.userLocation.value
        val deviceHash = kotlin.math.abs(senderId.hashCode()) % 100
        val microOffsetLat = (deviceHash - 50) * 0.000008
        val microOffsetLon = (deviceHash - 50) * 0.000008

        val latVal = if (uLoc.latitude != 0.0) uLoc.latitude else 13.000260 + microOffsetLat
        val lonVal = if (uLoc.longitude != 0.0) uLoc.longitude else 74.796070 + microOffsetLon

        val frame = ResQFrame(
            mtype = MessageType.MSG,
            srcId = if (senderId.isNotBlank()) senderId else "CIVILIAN",
            destId = destId,
            famId = "TEAM_A",
            prio = priority,
            lat = latVal,
            lon = lonVal,
            text = text,
            ttl = 4
        )
        val bleOk = bleManager.sendFrame(frame)
        wifiNodeManager.sendFrameOverWifi(frame)
        udpMeshManager.sendFrame(frame)
        onIncomingFrameReceived(frame, source = "LOCAL")
        Log.i("ResQMesh_VM", "⚡ Broadcast Outgoing Custom Msg (BLE ok: $bleOk): ${frame.toMeshString()}")
    }

    fun simulateMeshTraffic() {
        val baseLat = if (sensorManager.userLocation.value.latitude != 0.0) sensorManager.userLocation.value.latitude else 12.971598
        val baseLon = if (sensorManager.userLocation.value.longitude != 0.0) sensorManager.userLocation.value.longitude else 77.594562

        val sampleNodes = listOf("RQ10A1", "RQ20B4", "RQ30C8", "RQ50EE")
        val sampleNode = sampleNodes.random()
        val microOffsetLat = (Random.nextDouble() - 0.5) * 0.00015
        val microOffsetLon = (Random.nextDouble() - 0.5) * 0.00015

        val simulatedFrames = listOf(
            ResQFrame(
                mtype = MessageType.ALERT,
                srcId = sampleNode,
                destId = "ALL",
                famId = "FAM01",
                prio = Priority.P1,
                lat = baseLat + microOffsetLat,
                lon = baseLon + microOffsetLon,
                text = "TRAPPED_VICTIM_IN_VOID_SPACE",
                ttl = 3,
                rssi = -52
            ),
            ResQFrame(
                mtype = MessageType.DISPATCH,
                srcId = "RQ50EE",
                destId = "ALL",
                famId = "COMMAND",
                prio = Priority.P1,
                lat = baseLat + microOffsetLat,
                lon = baseLon + microOffsetLon,
                text = "EVACUATE_ZONE_B_GAS_LEAK_DETECTED",
                ttl = 4,
                rssi = -45
            ),
            ResQFrame(
                mtype = MessageType.MSG,
                srcId = sampleNode,
                destId = "ALL",
                famId = "TEAM_B",
                prio = Priority.P2,
                lat = baseLat + microOffsetLat,
                lon = baseLon + microOffsetLon,
                text = "WATER_SUPPLIES_DEPLOYED_NEARBY",
                ttl = 4,
                rssi = -60
            )
        )

        val frameToInject = simulatedFrames.random()
        viewModelScope.launch {
            onIncomingFrameReceived(frameToInject)
        }
    }

    fun runTriageAnalysis(frame: ResQFrame) {
        viewModelScope.launch {
            _activeTriageFrame.value = frame
            _isTriageLoading.value = true
            _activeTriageAnalysis.value = null
            val analysis = triageEngine.analyzeFrame(frame)
            _activeTriageAnalysis.value = analysis
            _isTriageLoading.value = false
        }
    }

    fun dismissTriageSheet() {
        _activeTriageFrame.value = null
        _activeTriageAnalysis.value = null
    }
}
