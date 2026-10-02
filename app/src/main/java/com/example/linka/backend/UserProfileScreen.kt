package com.example.linka.backend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Reached by tapping a chat message's sender (see ChatScreen's onSenderClick) - a
 * minimal public profile with the three actions requested: message them directly,
 * block them, or start a group with just the two of you. Nothing here is a full
 * social profile (no follower counts, no posts) - just enough to act on. */
@Composable
fun UserProfileScreen(
    themeColor: Color,
    isDark: Boolean,
    token: String,
    userId: String,
    isBlocked: Boolean,
    onBack: () -> Unit,
    onOpenChat: (userId: String, username: String) -> Unit,
    onBlocked: (userId: String) -> Unit,
    onUnblocked: (userId: String) -> Unit,
    onGroupCreated: (groupId: String, groupName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<BackendUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGroupForm by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(userId) {
        loading = true
        when (val result = BackendClient.getUser(token, userId)) {
            is BackendResult.Success -> user = result.value
            is BackendResult.Failure -> error = result.message
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "◀",
                color = themeColor,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
            )
            Text(
                text = "PROFILE",
                color = themeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(20.dp))

        when {
            loading -> CircularProgressIndicator(color = themeColor, modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, color = Color(0xFFFF6B6B), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            user != null -> {
                val currentUser = user!!
                Text(
                    text = currentUser.displayName.ifBlank { currentUser.username },
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("@${currentUser.username}", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                if (currentUser.bio.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(currentUser.bio, color = if (isDark) Color.White else Color.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(24.dp))

                if (showGroupForm) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(
                            onClick = {
                                if (groupName.isNotBlank()) {
                                    busy = true
                                    scope.launch {
                                        when (val result = BackendClient.createGroupWith(token, groupName.trim(), userId)) {
                                            is BackendResult.Success -> onGroupCreated(result.value.id, result.value.name)
                                            is BackendResult.Failure -> error = result.message
                                        }
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                        ) {
                            Text("CREATE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { showGroupForm = false; groupName = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f))
                        ) {
                            Text("CANCEL", color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                } else {
                    ProfileActionButton(
                        label = "CHAT",
                        color = themeColor,
                        textColor = Color.Black,
                        onClick = { onOpenChat(currentUser.id, currentUser.username) }
                    )
                    Spacer(Modifier.height(10.dp))
                    ProfileActionButton(
                        label = "CREATE GROUP WITH ${currentUser.username.uppercase()}",
                        color = Color.Gray.copy(alpha = 0.2f),
                        textColor = if (isDark) Color.White else Color.Black,
                        onClick = { showGroupForm = true }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (isBlocked) {
                        ProfileActionButton(
                            label = "UNBLOCK",
                            color = Color.Gray.copy(alpha = 0.2f),
                            textColor = if (isDark) Color.White else Color.Black,
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    BackendClient.unblockUser(token, userId)
                                    onUnblocked(userId)
                                    busy = false
                                }
                            }
                        )
                    } else {
                        ProfileActionButton(
                            label = "BLOCK",
                            color = Color(0xFF3A1414),
                            textColor = Color(0xFFFF6B6B),
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    BackendClient.blockUser(token, userId)
                                    onBlocked(userId)
                                    busy = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    label: String,
    color: Color,
    textColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
