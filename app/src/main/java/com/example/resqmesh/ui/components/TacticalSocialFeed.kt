package com.example.resqmesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.model.Priority
import com.example.resqmesh.model.ResQFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TacticalSocialFeed(
    frames: List<ResQFrame>,
    currentUserId: String = "MOBILE",
    isDarkMode: Boolean = false,
    onLockTarget: (nodeId: String, lat: Double, lon: Double) -> Unit,
    onSendMessage: (text: String, destId: String, priority: Priority) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val upvoteMap = remember { mutableStateMapOf<String, Int>() }
    var chatInputText by remember { mutableStateOf("") }

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
            // Tabs: 0 -> Instagram-style Surrounding Feed, 1 -> Two-Way Mesh Chatbox
            @Suppress("DEPRECATION")
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = innerBg,
                contentColor = Color(0xFFFF3D00),
                indicator = { tabPositions ->
                    @Suppress("DEPRECATION")
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFFFF3D00)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DynamicFeed, contentDescription = "Feed", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FIELD POSTS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CHATBOX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                if (frames.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(innerBg, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "No condition reports yet...",
                            color = subTextColor,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(frames) { frame ->
                            val frameId = "${frame.srcId}_${frame.seqId}_${frame.timestamp}"
                            val upvoteCount = upvoteMap[frameId] ?: 0

                            SurroundingConditionCard(
                                frame = frame,
                                upvoteCount = upvoteCount,
                                isDarkMode = isDarkMode,
                                onUpvote = { upvoteMap[frameId] = upvoteCount + 1 },
                                onLockTarget = { onLockTarget(frame.srcId, frame.lat, frame.lon) }
                            )
                        }
                    }
                }
            } else {
                if (frames.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(innerBg, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "Chat history empty. Type below to chat...",
                            color = subTextColor,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(frames) { frame ->
                            val isMe = frame.srcId == currentUserId || frame.srcId == "MOBILE"
                            ChatBubble(frame = frame, isMe = isMe, isDarkMode = isDarkMode)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fast Inline Chat Bar (Always visible for fast broadcast)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = { Text("Type message to all nodes...", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (chatInputText.isNotBlank()) {
                                onSendMessage(chatInputText, "ALL", Priority.P2)
                                chatInputText = ""
                            }
                        }
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (chatInputText.isNotBlank()) {
                            onSendMessage(chatInputText, "ALL", Priority.P2)
                            chatInputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFFF3D00), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SurroundingConditionCard(
    frame: ResQFrame,
    upvoteCount: Int,
    isDarkMode: Boolean,
    onUpvote: () -> Unit,
    onLockTarget: () -> Unit
) {
    val innerBg = if (isDarkMode) Color(0xFF101418) else Color(0xFFF8FAFC)
    val textColor = if (isDarkMode) Color.White else Color(0xFF1A202C)
    val subTextColor = if (isDarkMode) Color(0xFFA0AAB0) else Color(0xFF64748B)

    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = timeFormatter.format(Date(frame.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = innerBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFFF3D00), CircleShape)
                    ) {
                        Text(
                            text = frame.srcId.takeLast(2),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ROUTE: ${frame.routeDisplay}",
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$timeStr | HOPS: ${frame.hopCount} | TTL: ${frame.ttl}",
                            color = subTextColor,
                            fontSize = 9.sp
                        )
                    }
                }

                Text(
                    text = "[${frame.prio.name}]",
                    color = Color(0xFFFF3D00),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = frame.text,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (frame.lat != 0.0 && frame.lon != 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(if (isDarkMode) Color(0xFF1E242B) else Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Loc", tint = Color(0xFF00C853), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GPS: %.5f, %.5f".format(Locale.US, frame.lat, frame.lon),
                        color = Color(0xFF00C853),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onUpvote,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(imageVector = Icons.Default.ThumbUp, contentDescription = "Verify", tint = Color(0xFF0288D1), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VERIFIED ($upvoteCount)",
                        color = Color(0xFF0288D1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onLockTarget,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Route", tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ROUTE MAP",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    frame: ResQFrame,
    isMe: Boolean,
    isDarkMode: Boolean
) {
    val bubbleBg = if (isMe) Color(0xFFFF3D00) else if (isDarkMode) Color(0xFF101418) else Color(0xFFE2E8F0)
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val textColor = if (isMe) Color.White else if (isDarkMode) Color.White else Color(0xFF1A202C)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 10.dp,
                topEnd = 10.dp,
                bottomStart = if (isMe) 10.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 10.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleBg),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = if (isMe) "YOU -> ${frame.destId}" else "${frame.srcId} -> ${frame.destId}",
                    color = if (isMe) Color.White.copy(alpha = 0.8f) else Color(0xFF0288D1),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = frame.text,
                    color = textColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}
