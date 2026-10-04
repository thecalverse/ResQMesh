package com.example.resqmesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.resqmesh.model.Priority
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.resqmesh.model.UserProfile
import com.example.resqmesh.ui.components.AiTriageModal
import com.example.resqmesh.ui.components.DispatchDialog
import com.example.resqmesh.ui.components.EmergencyFeed
import com.example.resqmesh.ui.components.NearbyNodesRadarDialog
import com.example.resqmesh.ui.components.OsmMapView
import com.example.resqmesh.ui.components.ProximityAndCompass
import com.example.resqmesh.ui.components.SosAndNotesControl
import com.example.resqmesh.ui.components.TacticalStrip
import com.example.resqmesh.ui.components.UserProfileDialog
import com.example.resqmesh.ui.theme.ResQMeshTheme
import com.example.resqmesh.viewmodel.TacticalViewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val viewModel: TacticalViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (locationGranted) {
            viewModel.startSensorsAndBle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            ResQMeshTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TacticalDashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            viewModel.startSensorsAndBle()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startSensorsAndBle()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopSensorsAndBle()
    }
}

@Composable
fun TacticalDashboardScreen(
    viewModel: TacticalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val bleDiscoveredNodes by viewModel.bleManager.discoveredNodes.collectAsState()

    var showRadarDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(false) }

    // Persistent User Profile with Auto-Age Calculation & Unique Device ID
    val prefs = remember { context.getSharedPreferences("resqmesh_profile", Context.MODE_PRIVATE) }
    var userProfile by remember {
        val savedName = prefs.getString("user_name", null)
        val modelTag = Build.MODEL.replace(" ", "").takeLast(4).uppercase(java.util.Locale.ROOT)
        val randomNum = kotlin.random.Random.nextInt(100, 999)
        val uniqueDefaultName = "CIV_${modelTag}_$randomNum"

        val name = if (savedName.isNullOrBlank() || savedName == "CIVILIAN") {
            prefs.edit().putString("user_name", uniqueDefaultName).apply()
            uniqueDefaultName
        } else {
            savedName
        }

        val birthYear = prefs.getInt("birth_year", 2000)
        val dobCal = Calendar.getInstance().apply { set(birthYear, Calendar.JANUARY, 1) }
        mutableStateOf(UserProfile(name = name, birthdateEpochMs = dobCal.timeInMillis))
    }

    val appBgColor = if (isDarkMode) Color(0xFF101418) else Color(0xFFF8FAFC)
    val headerTextColor = if (isDarkMode) Color.White else Color(0xFF1E293B)

    Column(
        modifier = modifier
            .background(appBgColor)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Simple Action Bar with Official ResQMesh Logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isDarkMode = !isDarkMode },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Theme",
                        tint = if (isDarkMode) Color(0xFFFFD600) else Color(0xFF1E293B)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Image(
                    painter = painterResource(id = R.drawable.resqmesh_user_logo),
                    contentDescription = "ResQMesh Logo",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "ResQMesh",
                    color = headerTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile & Auto-Calculated Age Button
                OutlinedButton(
                    onClick = { showProfileDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color(0xFF0288D1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${userProfile.name} (${userProfile.age}y)",
                        color = Color(0xFF0288D1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Radar Button showing total BLE nodes in range
                OutlinedButton(
                    onClick = { showRadarDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar",
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BLE NODES (${bleDiscoveredNodes.size})",
                        color = Color(0xFF00C853),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // System Warning Banner if GPS is OFF in phone settings
        if (!uiState.isGpsEnabled) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.sensorManager.promptEnableGps() }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFD50000),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚠️ TAP TO ENABLE LOCATION / GPS",
                            color = Color(0xFFD50000),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Android OS requires Location Permission to scan BLE nodes.",
                            color = Color(0xFFC62828),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // 1. Tactical Strip Header (With 1-Tap Unlock Target Option)
        TacticalStrip(
            uiState = uiState,
            isDarkMode = isDarkMode,
            onEnableRadio = {
                viewModel.sendCustomMessage("P2P_MESH_TEST_REPORT", "ALL", Priority.P2, senderId = userProfile.name)
            },
            onClearTargetLock = {
                viewModel.clearTargetLock()
            }
        )

        // 2. Big Vector OpenStreetMap (340dp height)
        OsmMapView(
            uiState = uiState,
            currentUserId = userProfile.name,
            discoveredBleNodes = bleDiscoveredNodes,
            isDarkMode = isDarkMode,
            onConnectToBleNode = { address -> viewModel.connectToBleDevice(address) },
            onMarkerClick = { nodeId, lat, lon ->
                viewModel.lockTarget(nodeId, lat, lon)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        )

        // 3. Proximity Homing Meter & Vector Compass Arrow (With Unlock Target Option)
        ProximityAndCompass(
            uiState = uiState,
            isDarkMode = isDarkMode,
            onClearTargetLock = {
                viewModel.clearTargetLock()
            }
        )

        // 4. One-Tap SOS Quick Preset Buttons & Custom Field Notes
        SosAndNotesControl(
            onSendQuickSos = { preset -> viewModel.sendQuickSos("${userProfile.displayTag} - $preset", senderId = userProfile.name) },
            onSendCustomMessage = { text, destId, prio -> viewModel.sendCustomMessage(text, destId, prio, senderId = userProfile.name) },
            isDarkMode = isDarkMode
        )

        // 5. Live Emergency Mesh Feed Log with Target Tracking & Coordinates
        EmergencyFeed(
            frames = uiState.framesList,
            lockedTargetNodeId = uiState.lockedTarget?.nodeId,
            currentUserId = userProfile.name,
            userLat = uiState.userLocation.latitude,
            userLon = uiState.userLocation.longitude,
            isDarkMode = isDarkMode,
            onLockTarget = { nodeId, lat, lon ->
                if (uiState.lockedTarget?.nodeId == nodeId) {
                    viewModel.clearTargetLock()
                } else {
                    viewModel.lockTarget(nodeId, lat, lon)
                }
            },
            onRunTriage = { frame -> viewModel.runTriageAnalysis(frame) }
        )

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Modal Overlays
    // User Settings Profile Dialog (Name & Birth Year with Auto-Calculated Age!)
    if (showProfileDialog) {
        UserProfileDialog(
            currentProfile = userProfile,
            isDarkMode = isDarkMode,
            onSaveProfile = { newName, birthYear ->
                prefs.edit()
                    .putString("user_name", newName)
                    .putInt("birth_year", birthYear)
                    .apply()

                val dobCal = Calendar.getInstance().apply { set(birthYear, Calendar.JANUARY, 1) }
                userProfile = UserProfile(name = newName, birthdateEpochMs = dobCal.timeInMillis)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    // Nearby BLE Nodes Radar Dialog
    if (showRadarDialog) {
        NearbyNodesRadarDialog(
            discoveredBleNodes = bleDiscoveredNodes,
            connectedNodeName = uiState.connectedDeviceName,
            onConnectToBleNode = { address ->
                viewModel.connectToBleDevice(address)
                showRadarDialog = false
            },
            onDismiss = { showRadarDialog = false }
        )
    }

    // Official Dispatch High-Priority Intercept
    uiState.activeDispatchFrame?.let { dispatchFrame ->
        DispatchDialog(
            frame = dispatchFrame,
            onDismiss = { viewModel.dismissDispatchDialog() },
            onLockTarget = { nodeId, lat, lon -> viewModel.lockTarget(nodeId, lat, lon) }
        )
    }

    // Gemini AI Triage Analysis Sheet
    if (uiState.activeTriageFrame != null || uiState.isTriageLoading) {
        AiTriageModal(
            frame = uiState.activeTriageFrame,
            analysis = uiState.activeTriageAnalysis,
            isLoading = uiState.isTriageLoading,
            onDismiss = { viewModel.dismissTriageSheet() }
        )
    }
}
