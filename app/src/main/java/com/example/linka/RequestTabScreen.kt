package com.example.linka

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phonelinkagent.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RequestStatus { PENDING, ACCEPTED, DENIED, CANCELED }

private enum class RequestSubTab(val label: String) {
    MINE("MY REQUESTS"), RESPOND("RESPOND"), ACCEPTED("ACCEPTED")
}

private enum class InstalledFilter(val label: String) {
    ALL("ALL"), INSTALLED("APPS I HAVE"), NOT_INSTALLED("APPS I DON'T")
}

/** Backed by the real backend now (see backend/BackendClient.kt) - not age-gated, not
 * rate-limited, no report/block: those all need infrastructure (age verification, a
 * moderation system) that doesn't exist yet - deliberately not stubbed with fake
 * versions of any of that, since a fake gate is worse than an honest "not built yet"
 * notice.
 *
 * "MY REQUESTS" (the creator's view - cancel only), "RESPOND" (a responder's view -
 * accept/deny others' pending requests), and "ACCEPTED" (what you've accepted) are
 * three genuinely different server-side views now, not one shared list rendered three
 * ways - the server already excludes your own requests from RESPOND and won't let you
 * accept/deny your own (see server/src/routes/requests.ts).
 */
data class DomainRequest(
    val id: String,
    val summary: String,
    val reason: String,
    val timestampMs: Long,
    val status: RequestStatus = RequestStatus.PENDING,
)

@Composable
fun RequestTabScreen(
    themeColor: Color,
    isDark: Boolean,
    myRequests: List<DomainRequest>,
    respondRequests: List<DomainRequest>,
    acceptedRequests: List<DomainRequest>,
    myPairingToken: String,
    onAddRequest: (summary: String, reason: String) -> Unit,
    onRespondToRequest: (id: String, accepted: Boolean) -> Unit,
    onCancelRequest: (id: String) -> Unit,
    onAcceptedNavigateToManageApps: () -> Unit
) {
    val context = LocalContext.current
    var showForm by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var subTab by remember { mutableStateOf(RequestSubTab.MINE) }
    var installedFilter by remember { mutableStateOf(InstalledFilter.ALL) }
    var installedLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAcceptWarningId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        installedLabels = withContext(Dispatchers.Default) { installedAppLabels(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF060911) else Color(0xFFEFEFEF))
            .systemBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 40.dp)
    ) {
        Text(
            text = "REQUEST",
            color = themeColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(16.dp))

        if (showForm) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = "What are you requesting, and why?",
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("What (app/account)", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Button(
                        onClick = {
                            if (summary.isNotBlank() && reason.isNotBlank()) {
                                onAddRequest(summary.trim(), reason.trim())
                                summary = ""
                                reason = ""
                                showForm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("POST", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { showForm = false; summary = ""; reason = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f))
                    ) {
                        Text("CANCEL", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RequestSubTab.entries.forEach { tab ->
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
            Spacer(Modifier.height(10.dp))

            when (subTab) {
                RequestSubTab.MINE -> MyRequestsSection(
                    themeColor = themeColor,
                    isDark = isDark,
                    requests = myRequests,
                    onCancelRequest = onCancelRequest
                )
                RequestSubTab.RESPOND -> RespondSection(
                    themeColor = themeColor,
                    isDark = isDark,
                    requests = respondRequests,
                    installedLabels = installedLabels,
                    filter = installedFilter,
                    onFilterChange = { installedFilter = it },
                    onAccept = { id, installed ->
                        if (installed) {
                            onRespondToRequest(id, true)
                            onAcceptedNavigateToManageApps()
                        } else {
                            pendingAcceptWarningId = id
                        }
                    },
                    onDeny = { id -> onRespondToRequest(id, false) }
                )
                RequestSubTab.ACCEPTED -> AcceptedSection(
                    themeColor = themeColor,
                    isDark = isDark,
                    requests = acceptedRequests,
                    myPairingToken = myPairingToken
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ NEW REQUEST", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }

    val warningId = pendingAcceptWarningId
    if (warningId != null) {
        AlertDialog(
            onDismissRequest = { pendingAcceptWarningId = null },
            title = { Text("Doesn't look installed", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This app doesn't look like it's on your device, based on its name - " +
                        "accepting won't actually be able to share it. Accept anyway?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRespondToRequest(warningId, true)
                    onAcceptedNavigateToManageApps()
                    pendingAcceptWarningId = null
                }) {
                    Text("ACCEPT ANYWAY", color = themeColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAcceptWarningId = null }) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.MyRequestsSection(
    themeColor: Color,
    isDark: Boolean,
    requests: List<DomainRequest>,
    onCancelRequest: (id: String) -> Unit
) {
    val pending = requests.count { it.status == RequestStatus.PENDING }
    val accepted = requests.count { it.status == RequestStatus.ACCEPTED }
    val denied = requests.count { it.status == RequestStatus.DENIED }
    val canceled = requests.count { it.status == RequestStatus.CANCELED }
    Text(
        text = "$pending pending · $accepted accepted · $denied denied · $canceled canceled",
        color = Color.Gray,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
    )
    Spacer(Modifier.height(10.dp))
    if (requests.isEmpty()) {
        EmptyNotice("No requests yet")
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(requests, key = { it.id }) { request ->
                RequestCard(themeColor, isDark, request) {
                    if (request.status == RequestStatus.PENDING) {
                        Button(
                            onClick = { onCancelRequest(request.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("CANCEL", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        StatusLabel(themeColor, request.status)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RespondSection(
    themeColor: Color,
    isDark: Boolean,
    requests: List<DomainRequest>,
    installedLabels: List<String>,
    filter: InstalledFilter,
    onFilterChange: (InstalledFilter) -> Unit,
    onAccept: (id: String, installed: Boolean) -> Unit,
    onDeny: (id: String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InstalledFilter.entries.forEach { f ->
            val selected = filter == f
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) themeColor.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.08f))
                    .clickable { onFilterChange(f) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = f.label,
                    color = if (selected) themeColor else Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Server already returns only others' PENDING requests here (see
    // GET /requests in server/src/routes/requests.ts) - no local status filtering needed.
    val filtered = requests.filter { request ->
        val installed = requestMatchesInstalledApp(request.summary, installedLabels)
        when (filter) {
            InstalledFilter.ALL -> true
            InstalledFilter.INSTALLED -> installed
            InstalledFilter.NOT_INSTALLED -> !installed
        }
    }

    if (filtered.isEmpty()) {
        EmptyNotice(if (requests.isEmpty()) "Nothing to respond to yet" else "No requests match this filter")
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.id }) { request ->
                val installed = requestMatchesInstalledApp(request.summary, installedLabels)
                RequestCard(themeColor, isDark, request) {
                    Text(
                        text = if (installed) "✓ looks like you have this app" else "⚠ doesn't look installed on your device",
                        color = if (installed) themeColor else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onAccept(request.id, installed) },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("ACCEPT", color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onDeny(request.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("DENY", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AcceptedSection(
    themeColor: Color,
    isDark: Boolean,
    requests: List<DomainRequest>,
    myPairingToken: String
) {
    if (requests.isEmpty()) {
        EmptyNotice("Nothing accepted yet")
        return
    }
    val qrBitmap = remember(myPairingToken) { generateQrBitmap(myPairingToken) }
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(requests, key = { it.id }) { request ->
            RequestCard(themeColor, isDark, request) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Share this so they can link to you while you're sharing it:",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(6.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(myPairingToken, color = themeColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    themeColor: Color,
    isDark: Boolean,
    request: DomainRequest,
    footer: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Gray.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(
            text = request.summary,
            color = if (isDark) Color.White else Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = request.reason,
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        footer()
    }
}

@Composable
private fun StatusLabel(themeColor: Color, status: RequestStatus) {
    val (text, color) = when (status) {
        RequestStatus.PENDING -> "PENDING" to Color.Gray
        RequestStatus.ACCEPTED -> "✓ ACCEPTED" to themeColor
        RequestStatus.DENIED -> "✕ DENIED" to Color.Gray
        RequestStatus.CANCELED -> "CANCELED" to Color.Gray
    }
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
}

@Composable
private fun EmptyNotice(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
