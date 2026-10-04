package com.example.resqmesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.ble.BleConnectionState
import com.example.resqmesh.viewmodel.TacticalUiState
import java.util.Locale

@Composable
fun TacticalStrip(
    uiState: TacticalUiState,
    isDarkMode: Boolean = false,
    onEnableRadio: () -> Unit = {},
    onClearTargetLock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) Color(0xFF1E242B) else Color.White
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)
    val subTextColor = if (isDarkMode) Color(0xFFA0AAB0) else Color(0xFF718096)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // System Identifier
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFF3D00), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESQMESH TACTICAL",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onEnableRadio,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "⚡ MESH TEST",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Connection Badge (100% BLE Driven)
                val bleStatusColor = when (uiState.connectionState) {
                    BleConnectionState.CONNECTED -> Color(0xFF00C853)
                    BleConnectionState.CONNECTING -> Color(0xFFFFAB00)
                    BleConnectionState.SCANNING -> Color(0xFF0288D1)
                    BleConnectionState.DISCONNECTED -> Color(0xFFD50000)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(bleStatusColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .clickable { if (uiState.connectionState == BleConnectionState.DISCONNECTED) onEnableRadio() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = when (uiState.connectionState) {
                            BleConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                            BleConnectionState.DISCONNECTED -> Icons.Default.BluetoothDisabled
                            else -> Icons.AutoMirrored.Filled.BluetoothSearching
                        },
                        contentDescription = "BLE Status",
                        tint = bleStatusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (uiState.connectionState) {
                            BleConnectionState.CONNECTED -> uiState.connectedDeviceName ?: "BLE CONNECTED"
                            BleConnectionState.CONNECTING -> "CONNECTING..."
                            BleConnectionState.SCANNING -> "BLE SCANNING..."
                            BleConnectionState.DISCONNECTED -> "BLE DISCONNECTED"
                        },
                        color = bleStatusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Info Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Rescuer Live GPS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.userLocation.accuracy > 0) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = "GPS",
                        tint = if (uiState.userLocation.accuracy > 0) Color(0xFF00C853) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "RESCUER LOCATION",
                            color = subTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val latStr = if (uiState.userLocation.latitude != 0.0) "%.5f".format(Locale.US, uiState.userLocation.latitude) else "Locating..."
                        val lonStr = if (uiState.userLocation.longitude != 0.0) "%.5f".format(Locale.US, uiState.userLocation.longitude) else ""
                        Text(
                            text = if (lonStr.isNotEmpty()) "$latStr, $lonStr" else latStr,
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Active Locked Target Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Locked Target",
                        tint = if (uiState.lockedTarget != null) Color(0xFFFF3D00) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TARGET VICTIM",
                            color = subTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val target = uiState.lockedTarget
                        if (target != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${target.nodeId} (${"%.0fm".format(Locale.US, uiState.targetDistanceMeters)})",
                                    color = Color(0xFFFF3D00),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = onClearTargetLock,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Unlock Target",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "UNLOCK",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "TAP TO LOCK",
                                color = subTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
