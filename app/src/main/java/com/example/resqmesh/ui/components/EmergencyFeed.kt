package com.example.resqmesh.ui.components

import android.location.Location
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
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
import com.example.resqmesh.model.MessageType
import com.example.resqmesh.model.Priority
import com.example.resqmesh.model.ResQFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyFeed(
    frames: List<ResQFrame>,
    lockedTargetNodeId: String?,
    currentUserId: String = "",
    userLat: Double = 0.0,
    userLon: Double = 0.0,
    isDarkMode: Boolean = false,
    onLockTarget: (nodeId: String, lat: Double, lon: Double) -> Unit,
    onRunTriage: (frame: ResQFrame) -> Unit,
    modifier: Modifier = Modifier
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE MESH RADIO TRAFFIC (${frames.size})",
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "CHANNEL 1 ESP-NOW",
                    color = subTextColor,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (frames.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(innerBg, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Awaiting mesh radio traffic...",
                        color = subTextColor,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(frames) { frame ->
                        val isSelf = frame.srcId == currentUserId || (currentUserId.isBlank() && frame.srcId == "CIVILIAN")
                        EmergencyFrameCard(
                            frame = frame,
                            userLat = userLat,
                            userLon = userLon,
                            isLocked = lockedTargetNodeId == frame.srcId,
                            isSelf = isSelf,
                            isDarkMode = isDarkMode,
                            onLockTarget = { if (!isSelf) onLockTarget(frame.srcId, frame.lat, frame.lon) },
                            onRunTriage = { onRunTriage(frame) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyFrameCard(
    frame: ResQFrame,
    userLat: Double,
    userLon: Double,
    isLocked: Boolean,
    isSelf: Boolean,
    isDarkMode: Boolean,
    onLockTarget: () -> Unit,
    onRunTriage: () -> Unit
) {
    val cardBgColor = when {
        frame.prio == Priority.P1 || frame.mtype == MessageType.ALERT -> if (isDarkMode) Color(0xFF2C1014) else Color(0xFFFFEBEE)
        frame.mtype == MessageType.DISPATCH -> if (isDarkMode) Color(0xFF102030) else Color(0xFFE1F5FE)
        frame.prio == Priority.P2 -> if (isDarkMode) Color(0xFF2B2010) else Color(0xFFFFF8E1)
        else -> if (isDarkMode) Color(0xFF102218) else Color(0xFFE8F5E9)
    }

    val accentColor = when {
        frame.prio == Priority.P1 || frame.mtype == MessageType.ALERT -> Color(0xFFD50000)
        frame.mtype == MessageType.DISPATCH -> Color(0xFF0288D1)
        frame.prio == Priority.P2 -> Color(0xFFFFAB00)
        else -> Color(0xFF00C853)
    }

    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)
    val subTextColor = if (isDarkMode) Color(0xFFA0AAB0) else Color(0xFF64748B)

    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = timeFormatter.format(Date(frame.timestamp))

    // Calculate distance if both points exist
    var distMeters = 0.0
    if (userLat != 0.0 && userLon != 0.0 && frame.lat != 0.0 && frame.lon != 0.0) {
        val res = FloatArray(1)
        Location.distanceBetween(userLat, userLon, frame.lat, frame.lon, res)
        distMeters = res[0].toDouble()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isSelf) onLockTarget() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header Row: Sender ID, Message Type, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SENDER: ${frame.srcId}",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${frame.mtype.name} | ${frame.prio.name}]",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = timeStr,
                    color = subTextColor,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Payload Text
            Text(
                text = frame.text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // GPS Location & Distance Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Location",
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))

                val locationText = if (frame.lat != 0.0 && frame.lon != 0.0) {
                    val distStr = if (distMeters > 0) " (%.0fm away)".format(Locale.US, distMeters) else ""
                    "GPS: %.5f, %.5f$distStr".format(Locale.US, frame.lat, frame.lon)
                } else {
                    "GPS: Location Pending"
                }

                Text(
                    text = locationText,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Route & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROUTE: ${frame.routeDisplay} | HOPS:${frame.hopCount}",
                    color = subTextColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isSelf && frame.lat != 0.0 && frame.lon != 0.0) {
                        Button(
                            onClick = onLockTarget,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLocked) Color(0xFFFF3D00) else Color(0xFF0288D1)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Lock",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isLocked) "TARGET LOCKED" else "🎯 LOCK TARGET",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onRunTriage,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Triage",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "AI TRIAGE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
