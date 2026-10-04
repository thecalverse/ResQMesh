package com.example.resqmesh.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.resqmesh.model.ResQFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpMeshManager(private val context: Context) {

    companion object {
        private const val TAG = "ResQMesh_UDP"
        private const val UDP_PORT = 8888
        private const val BUFFER_SIZE = 2048
    }

    private val _incomingFrames = MutableSharedFlow<ResQFrame>(extraBufferCapacity = 512)
    val incomingFrames: SharedFlow<ResQFrame> = _incomingFrames.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var listenJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        acquireMulticastLock()
        startListening()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("ResQMesh_UDP_Lock").apply {
                    setReferenceCounted(true)
                    acquire()
                }
                Log.i(TAG, "⚡ Acquired Wi-Fi Multicast Lock for Direct UDP Mesh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring Multicast Lock: ${e.message}")
        }
    }

    fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                socket?.close()
                val ds = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(UDP_PORT))
                    broadcast = true
                }
                socket = ds
                Log.i(TAG, "⚡ Direct Phone-to-Phone UDP Mesh listening on Port $UDP_PORT...")

                val buffer = ByteArray(BUFFER_SIZE)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket?.receive(packet)

                        val rawData = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (rawData.isNotEmpty()) {
                            Log.i(TAG, "⚡ UDP Packet Received (${packet.address.hostAddress}): $rawData")
                            val frame = ResQFrame.parse(rawData, -45)
                            if (frame != null) {
                                _incomingFrames.emit(frame)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP Socket Listen Error: ${e.message}")
            }
        }
    }

    fun sendFrame(frame: ResQFrame) {
        scope.launch {
            try {
                val payload = frame.toMeshString() + "\n"
                val bytes = payload.toByteArray(Charsets.UTF_8)

                val targets = listOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("192.168.4.255"),
                    InetAddress.getByName("192.168.43.255"),
                    InetAddress.getByName("192.168.1.255")
                )

                val sendSocket = socket ?: DatagramSocket().apply { broadcast = true }
                for (target in targets) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, target, UDP_PORT)
                        sendSocket.send(packet)
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "⚡ Broadcast UDP Frame: ${payload.trim()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send UDP Frame: ${e.message}")
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
