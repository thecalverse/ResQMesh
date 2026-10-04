package com.example.resqmesh.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.resqmesh.ble.DiscoveredNode
import com.example.resqmesh.viewmodel.TacticalUiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File

@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMapView(
    uiState: TacticalUiState,
    currentUserId: String = "",
    discoveredBleNodes: List<DiscoveredNode> = emptyList(),
    isDarkMode: Boolean = false,
    onConnectToBleNode: (address: String) -> Unit = {},
    onMarkerClick: (nodeId: String, lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        val osmdroidConfig = Configuration.getInstance()
        osmdroidConfig.userAgentValue = "ResQMesh-Emergency-System/1.0 (com.example.resqmesh)"

        val basePath = File(context.cacheDir, "osmdroid_tiles")
        if (!basePath.exists()) basePath.mkdirs()
        osmdroidConfig.osmdroidBasePath = basePath
        osmdroidConfig.osmdroidTileCache = File(basePath, "tiles")
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.5)

            val scaleBarOverlay = ScaleBarOverlay(this)
            scaleBarOverlay.setAlignBottom(true)
            overlays.add(scaleBarOverlay)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val overlayBg = if (isDarkMode) Color(0xFF101418).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.95f)
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDarkMode) Color(0xFF1E242B) else Color(0xFFE2E8F0))
    ) {
        // Native MapView
        AndroidView(
            factory = {
                mapView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                val staticOverlays = map.overlays.take(1)
                map.overlays.clear()
                map.overlays.addAll(staticOverlays)

                val userLat = uiState.userLocation.latitude
                val userLon = uiState.userLocation.longitude
                var centerPoint = GeoPoint(13.00026, 74.79607)

                // 1. Rescuer Precision Location
                if (userLat != 0.0 && userLon != 0.0) {
                    val userPoint = GeoPoint(userLat, userLon)
                    centerPoint = userPoint

                    val accuracyMeters = if (uiState.userLocation.accuracy > 0) uiState.userLocation.accuracy.toDouble() else 3.0
                    val circlePoints = Polygon.pointsAsCircle(userPoint, accuracyMeters)
                    val accuracyCircle = Polygon().apply {
                        points = circlePoints
                        fillPaint.color = AndroidColor.argb(40, 0, 200, 83)
                        outlinePaint.color = AndroidColor.argb(180, 0, 200, 83)
                        outlinePaint.strokeWidth = 3f
                    }
                    map.overlays.add(accuracyCircle)

                    val userMarker = Marker(map).apply {
                        position = userPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "RESCUER (YOUR LOCATION)"
                        snippet = "Lat: %.6f, Lon: %.6f (±%.1fm)".format(userLat, userLon, accuracyMeters)
                    }
                    map.overlays.add(userMarker)
                }

                // 2. Node & Other Victim Pins (Excluding Own SOS)
                for (frame in uiState.framesList) {
                    if (frame.lat != 0.0 && frame.lon != 0.0) {
                        val isSelf = frame.srcId == currentUserId || (currentUserId.isBlank() && frame.srcId == "CIVILIAN")
                        if (!isSelf) {
                            val nodePoint = GeoPoint(frame.lat, frame.lon)

                            val marker = Marker(map).apply {
                                position = nodePoint
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "SOS: ${frame.srcId} [${frame.prio.name}]"
                                snippet = "${frame.text} (${frame.routeDisplay})"

                                setOnMarkerClickListener { _, _ ->
                                    onMarkerClick(frame.srcId, frame.lat, frame.lon)
                                    showInfoWindow()
                                    true
                                }
                            }
                            map.overlays.add(marker)
                        }
                    }
                }

                // 3. Target Route Line to External Victims in Distress
                val lockedTarget = uiState.lockedTarget
                if (lockedTarget != null && userLat != 0.0 && userLon != 0.0 && lockedTarget.nodeId != currentUserId) {
                    val targetPoint = GeoPoint(lockedTarget.lat, lockedTarget.lon)

                    val polyline = Polyline().apply {
                        setPoints(listOf(GeoPoint(userLat, userLon), targetPoint))
                        outlinePaint.color = AndroidColor.RED
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(polyline)

                    centerPoint = targetPoint
                }

                map.controller.setCenter(centerPoint)
                map.invalidate()
            }
        )

        // Overlay: Active Connection Callout & List of ALL BLE Nodes in Range
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(overlayBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = "BLE Status",
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "ACTIVE BLE NODE",
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.connectedDeviceName ?: "Scanning BLE Nodes...",
                            color = Color(0xFF00C853),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "${uiState.rssi} dBm",
                    color = Color(0xFF0288D1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // List of ALL Discovered BLE Nodes in Range
            if (discoveredBleNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.size(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(discoveredBleNodes) { bleNode ->
                        val isConnected = uiState.connectedDeviceName?.contains(bleNode.name) == true
                        FilterChip(
                            selected = isConnected,
                            onClick = { onConnectToBleNode(bleNode.address) },
                            label = {
                                Text(
                                    text = "${bleNode.name} (${bleNode.rssi}dBm)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                    contentDescription = "BLE Node",
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00C853),
                                containerColor = overlayBg
                            )
                        )
                    }
                }
            }
        }
    }
}
