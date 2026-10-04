package com.example.resqmesh.sensor

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log

class WifiManagerHelper(private val context: Context) {

    companion object {
        private const val TAG = "ResQMesh_Wifi"
    }

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Wi-Fi state: ${e.message}")
            false
        }
    }

    fun ensureWifiEnabled() {
        val manager = wifiManager ?: return
        try {
            if (!manager.isWifiEnabled) {
                Log.d(TAG, "Wi-Fi is disabled. Automatically attempting to enable Wi-Fi for ESP-NOW radio...")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    manager.isWifiEnabled = true
                } else {
                    val panelIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(panelIntent)
                }
            } else {
                Log.d(TAG, "Wi-Fi is already active.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Wi-Fi is enabled: ${e.message}")
        }
    }
}
