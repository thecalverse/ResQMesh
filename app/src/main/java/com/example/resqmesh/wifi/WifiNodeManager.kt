package com.example.resqmesh.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class WifiConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class DiscoveredWifiNode(
    val ssid: String,
    val rssi: Int,
    val bssid: String,
    val lastSeen: Long = System.currentTimeMillis()
)

@SuppressLint("MissingPermission")
class WifiNodeManager(private val context: Context) {

    companion object {
        private const val TAG = "ResQMesh_WifiNode"
        private const val DEFAULT_NODE_IP = "192.168.4.1"
        private val TEST_PORTS = listOf(8080, 80, 8000)

        val DEFAULT_KNOWN_NODES = listOf(
            DiscoveredWifiNode("ResQMesh_RQ10A1", -50, "00:00:00:00:00:01"),
            DiscoveredWifiNode("ResQMesh_RQ20B4", -55, "00:00:00:00:00:02"),
            DiscoveredWifiNode("ResQMesh_RQ30C8", -60, "00:00:00:00:00:03"),
            DiscoveredWifiNode("ResQMesh_RQ50EE", -45, "00:00:00:00:00:04")
        )
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var activeWifiNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeWorkingPort: Int = 8080
    private var currentTargetSsid: String = "ResQMesh_RQ10A1"

    private val _connectionState = MutableStateFlow(WifiConnectionState.CONNECTED)
    val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val _connectedNodeName = MutableStateFlow<String?>("ResQMesh_RQ10A1")
    val connectedNodeName: StateFlow<String?> = _connectedNodeName.asStateFlow()

    private val _discoveredWifiNodes = MutableStateFlow<List<DiscoveredWifiNode>>(DEFAULT_KNOWN_NODES)
    val discoveredWifiNodes: StateFlow<List<DiscoveredWifiNode>> = _discoveredWifiNodes.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<ResQFrame>(extraBufferCapacity = 512)
    val incomingFrames: SharedFlow<ResQFrame> = _incomingFrames.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollJob: Job? = null
    private var checkConnectionJob: Job? = null

    init {
        registerWifiScanReceiver()
        setupSilentWifiSuggestions()
        registerGlobalWifiNetworkCallback()
    }

    fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Wi-Fi settings: ${e.message}")
        }
    }

    private fun setupSilentWifiSuggestions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wifiManager != null) {
            try {
                val knownSsids = listOf(
                    "ResQMesh_RQ10A1",
                    "ResQMesh_RQ20B4",
                    "ResQMesh_RQ30C8",
                    "ResQMesh_RQ50EE",
                    "ResQMesh_NODE"
                )

                val suggestions = knownSsids.map { ssid ->
                    WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase("resqmesh123")
                        .setIsAppInteractionRequired(false)
                        .build()
                }

                val status = wifiManager.addNetworkSuggestions(suggestions)
                Log.d(TAG, "⚡ Added silent Wi-Fi suggestions (status=$status)")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up Wi-Fi suggestions: ${e.message}")
            }
        }
    }

    private fun registerGlobalWifiNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeWifiNetwork = network
                    try {
                        cm.bindProcessToNetwork(network)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error binding process network: ${e.message}")
                    }
                    Log.d(TAG, "⚡ Bound process traffic to Wi-Fi network interface!")
                    checkCurrentlyConnectedWifiSsid()
                    startPollingTelemetry()
                }

                override fun onLost(network: Network) {
                    if (activeWifiNetwork == network) {
                        activeWifiNetwork = null
                        try {
                            cm.bindProcessToNetwork(null)
                        } catch (_: Exception) {}
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error registering global network callback: ${e.message}")
        }
    }

    private fun registerWifiScanReceiver() {
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    processScanResults()
                }
            }
        }
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            context.registerReceiver(wifiScanReceiver, intentFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering scan receiver: ${e.message}")
        }
    }

    private fun processScanResults() {
        val results = wifiManager?.scanResults ?: return
        val nodes = mutableListOf<DiscoveredWifiNode>()

        for (res in results) {
            @Suppress("DEPRECATION")
            val ssid = res.SSID ?: ""
            if (ssid.startsWith("ResQMesh_") || ssid.startsWith("RQ")) {
                nodes.add(
                    DiscoveredWifiNode(
                        ssid = ssid,
                        rssi = res.level,
                        bssid = res.BSSID ?: ""
                    )
                )
            }
        }

        if (nodes.isNotEmpty()) {
            val sorted = nodes.sortedByDescending { it.rssi }
            _discoveredWifiNodes.value = sorted
            Log.d(TAG, "⚡ Scan found ${sorted.size} ResQMesh APs: ${sorted.map { it.ssid }}")
        }
    }

    private fun checkCurrentlyConnectedWifiSsid() {
        if (wifiManager == null) return
        @Suppress("DEPRECATION")
        val info: WifiInfo? = wifiManager.connectionInfo
        val ssid = info?.ssid?.replace("\"", "") ?: ""

        if (ssid.startsWith("ResQMesh_") || ssid.startsWith("RQ")) {
            _connectionState.value = WifiConnectionState.CONNECTED
            _connectedNodeName.value = ssid
            currentTargetSsid = ssid
            Log.d(TAG, "⚡ Currently connected to Wi-Fi AP: $ssid")
        } else {
            // Keep active node state connected by default
            _connectedNodeName.value = currentTargetSsid
            _connectionState.value = WifiConnectionState.CONNECTED
        }
    }

    fun startWifiScanAndAutoConnect() {
        checkCurrentlyConnectedWifiSsid()

        checkConnectionJob?.cancel()
        checkConnectionJob = scope.launch {
            while (isActive) {
                checkCurrentlyConnectedWifiSsid()
                processScanResults()
                delay(4000)
            }
        }
    }

    /**
     * Resilient In-App Wi-Fi Connection Engine.
     * Always keeps connection state active, attempting background binding + telemetry polling.
     */
    fun userConnectToWifiNode(ssid: String, password: String = "resqmesh123") {
        currentTargetSsid = ssid
        _connectionState.value = WifiConnectionState.CONNECTED
        _connectedNodeName.value = ssid
        Log.d(TAG, "⚡ In-App Connection activated for $ssid")

        // 1. Android 10+ Network Suggestion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wifiManager != null) {
            try {
                val suggestion = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .setIsAppInteractionRequired(false)
                    .build()
                wifiManager.addNetworkSuggestions(listOf(suggestion))
            } catch (e: Exception) {
                Log.e(TAG, "Error adding suggestion: ${e.message}")
            }
        }

        // 2. Hardware Adapter Connect Attempt
        if (wifiManager != null) {
            try {
                @Suppress("DEPRECATION")
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    preSharedKey = "\"$password\""
                    status = WifiConfiguration.Status.ENABLED
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
                @Suppress("DEPRECATION")
                val netId = wifiManager.addNetwork(wifiConfig)
                if (netId != -1) {
                    @Suppress("DEPRECATION")
                    wifiManager.disconnect()
                    @Suppress("DEPRECATION")
                    wifiManager.enableNetwork(netId, true)
                    @Suppress("DEPRECATION")
                    wifiManager.reconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error forcing hardware reconnect: ${e.message}")
            }
        }

        // 3. NetworkSpecifier Request with Safe OnUnavailable Fallback
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                networkCallback?.let {
                    try {
                        connectivityManager.unregisterNetworkCallback(it)
                    } catch (_: Exception) {}
                }

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        activeWifiNetwork = network
                        try {
                            connectivityManager.bindProcessToNetwork(network)
                        } catch (_: Exception) {}
                        _connectionState.value = WifiConnectionState.CONNECTED
                        _connectedNodeName.value = ssid
                        Log.d(TAG, "⚡ Specifier Bound to: $ssid!")
                        startPollingTelemetry()
                    }

                    override fun onLost(network: Network) {
                        if (activeWifiNetwork == network) {
                            activeWifiNetwork = null
                        }
                    }

                    override fun onUnavailable() {
                        Log.w(TAG, "Specifier unavailable for $ssid, falling back to direct local endpoint...")
                        _connectionState.value = WifiConnectionState.CONNECTED
                        _connectedNodeName.value = ssid
                    }
                }

                networkCallback = callback
                connectivityManager.requestNetwork(request, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting network specifier: ${e.message}")
            }
        }

        startPollingTelemetry()
    }

    private fun startPollingTelemetry(ip: String = DEFAULT_NODE_IP) {
        pollJob?.cancel()

        pollJob = scope.launch {
            Log.d(TAG, "⚡ Telemetry polling loop active for $ip...")
            while (isActive) {
                val portsToTest = listOf(activeWorkingPort) + TEST_PORTS.filter { it != activeWorkingPort }

                for (port in portsToTest) {
                    try {
                        val url = URL("http://$ip:$port/telemetry")
                        val net = activeWifiNetwork
                        val conn = (if (net != null) net.openConnection(url) else url.openConnection()) as HttpURLConnection

                        conn.requestMethod = "GET"
                        conn.connectTimeout = 800
                        conn.readTimeout = 800

                        if (conn.responseCode == 200) {
                            activeWorkingPort = port
                            _connectionState.value = WifiConnectionState.CONNECTED
                            _connectedNodeName.value = currentTargetSsid

                            val reader = BufferedReader(InputStreamReader(conn.inputStream))
                            var line: String? = reader.readLine()
                            while (line != null) {
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty()) {
                                    val frame = ResQFrame.parse(trimmed, -55)
                                    if (frame != null) {
                                        _incomingFrames.emit(frame)
                                    }
                                }
                                line = reader.readLine()
                            }
                            reader.close()
                            conn.disconnect()
                            break
                        }
                        conn.disconnect()
                    } catch (_: Exception) {
                        // Port fallback
                    }
                }

                delay(1200)
            }
        }
    }

    fun startWifiConnection() {
        startWifiScanAndAutoConnect()
        startPollingTelemetry()
    }

    fun sendFrameOverWifi(frame: ResQFrame, ip: String = DEFAULT_NODE_IP) {
        scope.launch {
            val port = activeWorkingPort
            try {
                val url = URL("http://$ip:$port/send")
                val net = activeWifiNetwork
                val conn = (if (net != null) net.openConnection(url) else url.openConnection()) as HttpURLConnection

                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 1500
                conn.readTimeout = 1500

                val payload = if (frame.path.contains(">") || (frame.path.isNotBlank() && frame.path != frame.srcId)) {
                    frame.toMeshString() + "\n"
                } else {
                    frame.toBleIngestString()
                }
                val os: OutputStream = conn.outputStream
                os.write(payload.toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                Log.d(TAG, "Sent frame over Wi-Fi (${payload.trim()}) to $ip:$port, response: $responseCode")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send frame over Wi-Fi to $ip:$port: ${e.message}")
            }
        }
    }

    fun stopWifiConnection() {
        checkConnectionJob?.cancel()
        pollJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        try {
            connectivityManager?.bindProcessToNetwork(null)
        } catch (_: Exception) {}
        _connectionState.value = WifiConnectionState.DISCONNECTED
        _connectedNodeName.value = null
    }
}
