package com.example.linka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A membership-scoped group chat, backed by the real backend now (see
 * backend/BackendClient.kt) - "groups" here is only what the signed-in account has
 * joined; see discoverableGroups for the rest. */
data class LocalGroup(val id: String, val name: String)

private enum class GroupsSubTab(val label: String) { MINE("MY GROUPS"), DISCOVER("DISCOVER") }

@Composable
fun GroupsTabScreen(
    themeColor: Color,
    isDark: Boolean,
    groups: List<LocalGroup>,
    discoverableGroups: List<LocalGroup>,
    messagesFor: (groupId: String) -> List<ChatEntry>,
    onCreateGroup: (name: String) -> Unit,
    onSendToGroup: (groupId: String, text: String) -> Unit,
    onJoinGroup: (groupId: String) -> Unit,
    onSenderClick: ((senderId: String, senderUsername: String) -> Unit)? = null
) {
    var openGroupId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var subTab by remember { mutableStateOf(GroupsSubTab.MINE) }

    val openGroup = groups.find { it.id == openGroupId }
    if (openGroup != null) {
        ChatScreen(
            themeColor = themeColor,
            isDark = isDark,
            messages = messagesFor(openGroup.id),
            onSend = { text -> onSendToGroup(openGroup.id, text) },
            onBack = { openGroupId = null },
            title = openGroup.name.uppercase(),
            onSenderClick = onSenderClick
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 40.dp)
    ) {
        Text(
            text = "GROUPS",
            color = themeColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(16.dp))

        if (showCreate) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group name", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Button(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                onCreateGroup(newGroupName.trim())
                                newGroupName = ""
                                showCreate = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("CREATE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { showCreate = false; newGroupName = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f))
                    ) {
                        Text("CANCEL", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupsSubTab.entries.forEach { tab ->
                    val selected = subTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) themeColor.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f))
                            .clickable { subTab = tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tab.label,
                            color = if (selected) themeColor else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            when (subTab) {
                GroupsSubTab.MINE -> {
                    if (groups.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No groups yet - join one from DISCOVER or create your own", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(groups, key = { it.id }) { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Gray.copy(alpha = 0.12f))
                                        .clickable { openGroupId = group.id }
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = group.name,
                                        color = if (isDark) Color.White else Color.Black,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(">", color = themeColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showCreate = true },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ NEW GROUP", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
                GroupsSubTab.DISCOVER -> {
                    if (discoverableGroups.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No other groups yet", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(discoverableGroups, key = { it.id }) { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Gray.copy(alpha = 0.12f))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = group.name,
                                        color = if (isDark) Color.White else Color.Black,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { onJoinGroup(group.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("JOIN", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
