package com.example.linka

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val logDateFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

private fun typeLabel(type: ActivityType): String = when (type) {
    ActivityType.LINK_ESTABLISHED -> "LINKED"
    ActivityType.REQUEST_CREATED -> "REQUEST POSTED"
    ActivityType.REQUEST_ACCEPTED -> "REQUEST ACCEPTED"
    ActivityType.REQUEST_DENIED -> "REQUEST DENIED"
}

private fun detailLine(entry: ActivityLogEntry): String = when (entry.type) {
    ActivityType.LINK_ESTABLISHED -> {
        val role = when (entry.role) {
            SessionRole.CONTROLLER -> "as CONTROLLER"
            SessionRole.CONTROLLEE -> "as CONTROLLEE"
            null -> "unknown role"
        }
        "$role · ${entry.otherCountry ?: "unknown"}"
    }
    ActivityType.REQUEST_CREATED, ActivityType.REQUEST_ACCEPTED, ActivityType.REQUEST_DENIED ->
        entry.requestSummary ?: ""
}

/** Reverse-chronological record of pairing history and request lifecycle events - see
 * PLANNING.md's activity log feature. Unlike chat/domain/groups, persisted (ActivityLogStore),
 * so it's meant to survive an app restart. Reached from Settings > ACTIVITY. */
@Composable
fun ActivityLogScreen(
    themeColor: Color,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = remember(context) { ActivityLogStore.all(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
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
                text = "ACTIVITY LOG",
                color = themeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No activity yet", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(entries, key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Gray.copy(alpha = 0.12f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = typeLabel(entry.type),
                                color = themeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = logDateFormat.format(Date(entry.timestampMs)),
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        val detail = detailLine(entry)
                        if (detail.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = detail,
                                color = if (isDark) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
