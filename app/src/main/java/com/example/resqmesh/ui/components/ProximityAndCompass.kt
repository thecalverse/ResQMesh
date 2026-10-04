package com.example.resqmesh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.viewmodel.TacticalUiState
import java.util.Locale

@Composable
fun ProximityAndCompass(
    uiState: TacticalUiState,
    isDarkMode: Boolean = false,
    onClearTargetLock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkMode) Color(0xFF1E242B) else Color.White
    val innerBg = if (isDarkMode) Color(0xFF101418) else Color(0xFFE8ECEF)
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)
    val subTextColor = if (isDarkMode) Color(0xFFA0AAB0) else Color(0xFF718096)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column: Vector Compass Arrow
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "TARGET DIRECTION",
                    color = subTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val animatedRotation by animateFloatAsState(
                    targetValue = uiState.relativeArrowAngle,
                    animationSpec = tween(durationMillis = 200),
                    label = "CompassRotation"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(60.dp)
                        .background(innerBg, shape = RoundedCornerShape(30.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Vector Arrow",
                        tint = if (uiState.lockedTarget != null) Color(0xFFFF3D00) else Color.Gray,
                        modifier = Modifier
                            .size(34.dp)
                            .rotate(animatedRotation)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                val distanceText = if (uiState.lockedTarget != null) {
                    if (uiState.targetDistanceMeters >= 1000) {
                        "%.2f km".format(Locale.US, uiState.targetDistanceMeters / 1000.0)
                    } else {
                        "%.0f meters".format(Locale.US, uiState.targetDistanceMeters)
                    }
                } else {
                    "Searching..."
                }

                Text(
                    text = distanceText,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (uiState.lockedTarget != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onClearTargetLock,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "UNLOCK TARGET",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Column: Proximity Signal Bar
            Column(
                modifier = Modifier.weight(1.2f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "RSSI",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SIGNAL METER",
                            color = subTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${uiState.rssi} dBm",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val normalizedRssi = ((uiState.rssi + 100) / 60f).coerceIn(0f, 1f)

                val rssiColor = when {
                    normalizedRssi > 0.65f -> Color(0xFF00C853)
                    normalizedRssi > 0.35f -> Color(0xFFFFAB00)
                    else -> Color(0xFFD50000)
                }

                LinearProgressIndicator(
                    progress = { normalizedRssi },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = rssiColor,
                    trackColor = innerBg,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        normalizedRssi > 0.75f -> "PROXIMITY: VERY CLOSE (<5m)"
                        normalizedRssi > 0.50f -> "PROXIMITY: CLOSING IN"
                        normalizedRssi > 0.20f -> "PROXIMITY: WEAK SIGNAL"
                        else -> "PROXIMITY: SEARCHING..."
                    },
                    color = rssiColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
