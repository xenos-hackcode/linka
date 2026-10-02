package com.example.linka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** #word addresses someone specific, @all tags everyone - see the caption on
 * ChatScreen's input row. Parsing/highlighting only, not routing: there's no real
 * user directory behind Global/Groups yet (see PLANNING.md), so a #mention can't
 * actually notify a specific person any differently than @all does today - this is
 * the visual convention now, ready to mean something once that infrastructure exists. */
private val mentionRegex = Regex("(#\\w+|@all)", RegexOption.IGNORE_CASE)

private fun highlightMentions(text: String, highlightColor: Color, normalColor: Color): AnnotatedString =
    buildAnnotatedString {
        var lastIndex = 0
        for (match in mentionRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = normalColor)) { append(text.substring(lastIndex, match.range.first)) }
            }
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) { append(match.value) }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            withStyle(SpanStyle(color = normalColor)) { append(text.substring(lastIndex)) }
        }
    }

/** Paired chat (controller<->controllee) stays in-memory only, riding the existing
 * socket - see PLANNING.md §3.3. Global/group/DM chat are backed by the real backend
 * now (see backend/BackendClient.kt) and set senderId/senderUsername so a message's
 * sender can be tapped to view their profile (see onSenderClick below); paired chat
 * leaves them null since there's no account behind it. */
data class ChatEntry(
    val text: String,
    val fromMe: Boolean,
    val timestampMs: Long,
    val senderId: String? = null,
    val senderUsername: String? = null,
)

/** Shared by both the controller and controllee side - PLANNING.md's paired-DM
 * feature is symmetric (either side can type to the other), and both packages now
 * live in one module post-merge, so one screen instead of two near-identical copies. */
@Composable
fun ChatScreen(
    themeColor: Color,
    isDark: Boolean,
    messages: List<ChatEntry>,
    onSend: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    noticeText: String? = null,
    title: String = "CHAT",
    onSenderClick: ((senderId: String, senderUsername: String) -> Unit)? = null
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text(
                    text = "◀",
                    color = themeColor,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
                )
            }
            Text(
                text = title,
                color = themeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (noticeText != null) {
            Spacer(Modifier.height(6.dp))
            Text(noticeText, color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No messages yet",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                items(messages) { entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = if (entry.fromMe) Alignment.End else Alignment.Start
                    ) {
                        val canViewSender = !entry.fromMe && entry.senderId != null && entry.senderUsername != null && onSenderClick != null
                        if (canViewSender) {
                            Text(
                                text = entry.senderUsername!!,
                                color = themeColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onSenderClick?.invoke(entry.senderId!!, entry.senderUsername) }
                                    .padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (entry.fromMe) themeColor.copy(alpha = 0.25f)
                                    else Color.Gray.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            val baseColor = if (isDark) Color.White else Color.Black
                            Text(
                                text = highlightMentions(entry.text, themeColor, baseColor),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        input = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) {
                Text("SEND", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "#name to address someone specific · @all to tag everyone",
            color = Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
