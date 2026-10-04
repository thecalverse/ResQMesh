package com.example.resqmesh.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.resqmesh.model.Priority
import kotlinx.coroutines.delay

@Composable
fun SosAndNotesControl(
    onSendQuickSos: (preset: String) -> Unit,
    onSendCustomMessage: (text: String, destId: String, priority: Priority) -> Unit,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var customText by remember { mutableStateOf("") }

    // 10-Second Countdown SOS Alert State
    var showCountdownDialog by remember { mutableStateOf(false) }
    var pendingSosPreset by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableIntStateOf(10) }

    val cardBg = if (isDarkMode) Color(0xFF1E242B) else Color.White
    val innerBg = if (isDarkMode) Color(0xFF101418) else Color(0xFFF1F5F9)
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)
    val subTextColor = if (isDarkMode) Color(0xFFA0AAB0) else Color(0xFF64748B)

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
            // Section 1: One-Tap SOS Quick Preset Buttons
            Text(
                text = "ONE-TAP SOS EMERGENCY BROADCAST (10s DELAY)",
                color = Color(0xFFFF3D00),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SosPresetButton(
                    label = "🚑 MEDICAL",
                    color = Color(0xFFD50000),
                    onClick = {
                        pendingSosPreset = "MEDICAL_EMERGENCY"
                        countdownSeconds = 10
                        showCountdownDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )
                SosPresetButton(
                    label = "🏚️ TRAPPED",
                    color = Color(0xFFFF6D00),
                    onClick = {
                        pendingSosPreset = "VICTIM_TRAPPED"
                        countdownSeconds = 10
                        showCountdownDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )
                SosPresetButton(
                    label = "💧 WATER",
                    color = Color(0xFF0288D1),
                    onClick = {
                        pendingSosPreset = "NEED_WATER_SUPPLIES"
                        countdownSeconds = 10
                        showCountdownDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )
                SosPresetButton(
                    label = "✅ SAFE",
                    color = Color(0xFF00C853),
                    onClick = {
                        pendingSosPreset = "RESCUER_SAFE"
                        countdownSeconds = 10
                        showCountdownDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Custom Field Note Input
            Text(
                text = "SEND MESH FIELD NOTE / CHAT REPORT",
                color = subTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Type report or situation update...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = innerBg,
                        unfocusedContainerColor = innerBg,
                        focusedBorderColor = Color(0xFFFF3D00),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (customText.isNotBlank()) {
                            onSendCustomMessage(customText, "ALL", Priority.P2)
                            customText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // 10-Second Countdown Emergency Alert Dialog
    if (showCountdownDialog) {
        LaunchedEffect(key1 = countdownSeconds, key2 = showCountdownDialog) {
            if (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds -= 1
            } else {
                // Ticked down to 0: Dispatch SOS Alert over mesh!
                onSendQuickSos(pendingSosPreset)
                showCountdownDialog = false
            }
        }

        Dialog(onDismissRequest = {
            showCountdownDialog = false
        }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1012)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning SOS",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "EMERGENCY SOS BROADCASTING",
                        color = Color(0xFFFF1744),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Broadcasting '$pendingSosPreset' across radio mesh in...",
                        color = Color(0xFFA0AAB0),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Large Pulsing Countdown Display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFFFF1744).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text(
                            text = "$countdownSeconds",
                            color = Color(0xFFFF1744),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons: Stop / Cancel SOS OR Send Immediately
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. CANCEL / ABORT BUTTON
                        Button(
                            onClick = {
                                showCountdownDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Cancel SOS",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🛑 CANCEL / STOP SOS (ABORT)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 2. SEND IMMEDIATELY OVERRIDE
                        Button(
                            onClick = {
                                onSendQuickSos(pendingSosPreset)
                                showCountdownDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Send Now",
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "⚡ SEND IMMEDIATELY (0s)",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SosPresetButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 2.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
