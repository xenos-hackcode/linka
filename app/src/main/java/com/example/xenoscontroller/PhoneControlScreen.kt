package com.example.xenoscontroller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.linka.ActivityLogEntry
import com.example.linka.ActivityLogStore
import com.example.linka.ActivityType
import com.example.linka.ChatEntry
import com.example.linka.ChatScreen
import com.example.linka.SessionRole
import com.example.linka.bestEffortCountry
import com.example.linka.core.message.AppAccessState
import com.example.linka.core.message.ChatMessage
import com.example.linka.core.message.ControlCommand
import com.example.linka.core.message.LocationInfo
import com.example.linka.core.message.PresenceEvent
import com.example.linka.core.socket.KtorControlSocketClient
import com.example.linka.core.socket.SocketConnState
import com.example.linka.core.socket.buildControlHttpClient
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PhoneControlConnState { DISCONNECTED, CONNECTING, WAITING_FOR_AGENT, CONNECTED }

/** Wire protocol and reconnect-with-backoff behavior now live in :core's
 * KtorControlSocketClient/LinkMessage - this wraps it with the same public surface this
 * screen already used, so PhoneLiveControl below needed no changes. Part of this standalone
 * controller app so someone can act as controller without installing the full
 * SciFiLauncher/Xenos launcher. */
class PhoneControlClient(private val token: String) {
    var onState: ((PhoneControlConnState) -> Unit)? = null
    var onFrame: ((Bitmap) -> Unit)? = null
    var onAppAccessChanged: ((Boolean) -> Unit)? = null
    var onChatMessage: ((String) -> Unit)? = null
    var onLocationReceived: ((String) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val socket = KtorControlSocketClient(
        url = "wss://elene-backend-717899371194.us-central1.run.app/phone/ws/controller/$token",
        httpClient = buildControlHttpClient(),
        scope = scope,
    )

    fun connect() {
        onState?.invoke(PhoneControlConnState.CONNECTING)
        scope.launch {
            // drop(1): skip the socket's initial pre-connect CLOSED placeholder so we
            // don't flash "Disconnected" before the first real connection attempt.
            socket.connectionState.drop(1).collect { state ->
                when (state) {
                    SocketConnState.CONNECTING -> onState?.invoke(PhoneControlConnState.CONNECTING)
                    SocketConnState.OPEN -> onState?.invoke(PhoneControlConnState.WAITING_FOR_AGENT)
                    SocketConnState.CLOSED -> onState?.invoke(PhoneControlConnState.DISCONNECTED)
                }
            }
        }
        scope.launch {
            socket.incoming.collect { message ->
                when (message) {
                    is PresenceEvent.AgentConnected -> onState?.invoke(PhoneControlConnState.CONNECTED)
                    is PresenceEvent.AgentDisconnected, is PresenceEvent.AgentOffline ->
                        onState?.invoke(PhoneControlConnState.WAITING_FOR_AGENT)
                    is AppAccessState -> onAppAccessChanged?.invoke(message.allowed)
                    is ChatMessage -> onChatMessage?.invoke(message.text)
                    is LocationInfo -> onLocationReceived?.invoke(message.country)
                    else -> Unit
                }
            }
        }
        scope.launch {
            socket.frames.collect { bytes ->
                val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                if (bmp != null) onFrame?.invoke(bmp)
            }
        }
        socket.connect()
    }

    fun disconnect() {
        socket.disconnect()
        scope.cancel()
    }

    fun sendTap(x: Float, y: Float) =
        socket.send(ControlCommand.Tap(x.toDouble(), y.toDouble()))

    fun sendSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) =
        socket.send(ControlCommand.Swipe(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(), durationMs))

    fun sendKeyText(text: String) =
        socket.send(ControlCommand.KeyText(text))

    fun sendKeyPress(key: String) =
        socket.send(ControlCommand.KeyPress(key))

    fun sendChat(text: String) =
        socket.send(ChatMessage(text))

    fun sendLocation(country: String) =
        socket.send(LocationInfo(country))
}

private fun controlVideoRect(outerSize: IntSize, aspect: Float, scale: Float, panOffset: Offset): Rect {
    if (outerSize.width <= 0 || outerSize.height <= 0 || aspect <= 0f) {
        return Rect(0f, 0f, 1f, 1f)
    }
    val outerAspect = outerSize.width.toFloat() / outerSize.height.toFloat()
    val preW: Float
    val preH: Float
    if (outerAspect > aspect) {
        preH = outerSize.height.toFloat()
        preW = preH * aspect
    } else {
        preW = outerSize.width.toFloat()
        preH = preW / aspect
    }
    val preLeft = (outerSize.width - preW) / 2f
    val preTop = (outerSize.height - preH) / 2f
    val centerX = preLeft + preW / 2f
    val centerY = preTop + preH / 2f
    val visW = preW * scale
    val visH = preH * scale
    val visLeft = centerX - visW / 2f + panOffset.x
    val visTop = centerY - visH / 2f + panOffset.y
    return Rect(visLeft, visTop, visLeft + visW, visTop + visH)
}

@Composable
fun PhoneControlScreen(
    themeColor: Color,
    isDark: Boolean,
    savedToken: String?,
    onSaveToken: (String) -> Unit,
    onForgetToken: () -> Unit,
    onScanQr: (onResult: (String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    if (savedToken.isNullOrBlank()) {
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
                    text = "LINK TO PHONE",
                    color = themeColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(16.dp))
            PhonePairingSetup(themeColor = themeColor, onSaveToken = onSaveToken, onScanQr = onScanQr)
        }
    } else {
        PhoneLiveControl(themeColor = themeColor, token = savedToken, onBack = onBack, onForgetToken = onForgetToken)
    }
}

@Composable
private fun PhonePairingSetup(
    themeColor: Color,
    onSaveToken: (String) -> Unit,
    onScanQr: (onResult: (String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("📱", color = themeColor, fontSize = 40.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Not linked yet",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "On the target phone, install the Xenos Link to Phone agent app and " +
                    "complete its disclosure and permission steps - it will show a pairing " +
                    "code. Enter it below.",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.trim() },
            label = { Text("Pairing code", fontFamily = FontFamily.Monospace) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onScanQr { scanned -> input = scanned.trim() } },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SCAN QR INSTEAD", color = Color.White, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { if (input.length >= 16) onSaveToken(input) },
            enabled = input.length >= 16,
            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LINK", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PhoneLiveControl(
    themeColor: Color,
    token: String,
    onBack: () -> Unit,
    onForgetToken: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val client = remember(token) { PhoneControlClient(token) }
    var connState by remember { mutableStateOf(PhoneControlConnState.CONNECTING) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var frameAspect by remember { mutableStateOf(9f / 19.5f) }
    var outerSize by remember { mutableStateOf(IntSize(1, 1)) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }

    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerMarker by remember { mutableStateOf<Offset?>(null) }
    // Defaults true so there's no flash-of-black before the first real AppAccessState
    // arrives from the controllee (see PLANNING.md §3.1).
    var appAllowed by remember { mutableStateOf(true) }
    var showChat by remember { mutableStateOf(false) }
    // In-memory only - lost on disconnect, no backend to persist against (PLANNING.md §3.3).
    val chatMessages = remember { mutableStateListOf<ChatEntry>() }
    // Local to this screen (not MainActivity's centralized back handling) - correctly
    // closes chat first while it's open, since this composable only enters/leaves
    // composition as the user navigates to/from this screen (see the equivalent
    // controllee-side handler in MainActivity for the same reasoning in more detail).
    BackHandler(enabled = showChat) { showChat = false }

    DisposableEffect(token) {
        client.onState = { newState ->
            val wasConnected = connState == PhoneControlConnState.CONNECTED
            connState = newState
            if (newState == PhoneControlConnState.CONNECTED && !wasConnected) {
                ActivityLogStore.append(
                    context,
                    ActivityLogEntry(
                        id = System.currentTimeMillis().toString(),
                        type = ActivityType.LINK_ESTABLISHED,
                        timestampMs = System.currentTimeMillis(),
                        role = SessionRole.CONTROLLER,
                    )
                )
                coroutineScope.launch {
                    val country = withContext(Dispatchers.IO) { bestEffortCountry(context) }
                    if (country != null) client.sendLocation(country)
                }
            }
        }
        client.onFrame = { bmp ->
            frame = bmp
            if (bmp.width > 0 && bmp.height > 0) frameAspect = bmp.width.toFloat() / bmp.height.toFloat()
        }
        client.onAppAccessChanged = { appAllowed = it }
        client.onChatMessage = { text ->
            chatMessages.add(ChatEntry(text, fromMe = false, timestampMs = System.currentTimeMillis()))
        }
        client.onLocationReceived = { country ->
            ActivityLogStore.updateLastLinkCountry(context, SessionRole.CONTROLLER, country)
        }
        client.connect()
        onDispose { client.disconnect() }
    }

    if (showChat) {
        ChatScreen(
            themeColor = themeColor,
            isDark = true,
            messages = chatMessages,
            onSend = { text ->
                chatMessages.add(ChatEntry(text, fromMe = true, timestampMs = System.currentTimeMillis()))
                client.sendChat(text)
            },
            onBack = { showChat = false }
        )
        return
    }

    val statusText = when (connState) {
        PhoneControlConnState.DISCONNECTED -> "Disconnected"
        PhoneControlConnState.CONNECTING -> "Connecting..."
        PhoneControlConnState.WAITING_FOR_AGENT -> "Waiting for the other phone..."
        PhoneControlConnState.CONNECTED -> "Connected"
    }
    val statusColor = if (connState == PhoneControlConnState.CONNECTED) themeColor else Color.Gray

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { outerSize = it }
            .then(if (appAllowed) Modifier.pointerInput(token) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multiTouch = false
                    var dragged = false
                    var dragStart: Offset? = null

                    fun normalizedPos(pos: Offset): Offset {
                        val rect = controlVideoRect(outerSize, frameAspect, scale, panOffset)
                        val nx = ((pos.x - rect.left) / rect.width).coerceIn(0f, 1f)
                        val ny = ((pos.y - rect.top) / rect.height).coerceIn(0f, 1f)
                        return Offset(nx, ny)
                    }

                    pointerMarker = normalizedPos(down.position)
                    dragStart = pointerMarker

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            multiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val rect = controlVideoRect(outerSize, frameAspect, scale, panOffset)
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            val maxOffsetX = (rect.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = (rect.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                            scale = newScale
                            panOffset = Offset(
                                (panOffset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                (panOffset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                            event.changes.forEach { it.consume() }
                        } else if (!multiTouch && pressed.isNotEmpty()) {
                            val change = pressed.first()
                            val dist = hypot(
                                (change.position.x - down.position.x).toDouble(),
                                (change.position.y - down.position.y).toDouble()
                            )
                            val n = normalizedPos(change.position)
                            if (dist > viewConfiguration.touchSlop) {
                                dragged = true
                                pointerMarker = n
                                change.consume()
                            }
                        }

                        if (event.changes.none { it.pressed }) break
                    }

                    if (!multiTouch) {
                        val end = pointerMarker ?: normalizedPos(down.position)
                        val start = dragStart ?: end
                        if (dragged) {
                            client.sendSwipe(start.x, start.y, end.x, end.y, 250L)
                        } else {
                            client.sendTap(end.x, end.y)
                        }
                    }
                }
            } else Modifier)
    ) {
        if (appAllowed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .aspectRatio(frameAspect)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentFrame = frame
                if (currentFrame != null) {
                    Image(
                        bitmap = currentFrame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No image yet", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                pointerMarker?.let { marker ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = marker.x * size.width
                        val cy = marker.y * size.height
                        drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(cx, cy), alpha = 0.4f)
                        drawCircle(color = themeColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This app isn't shared by the person you're linked to",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◀",
                color = themeColor,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 10.dp)
            )
            Text(statusText, color = statusColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(
                text = "💬 CHAT",
                color = themeColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { showChat = true }.padding(end = 10.dp)
            )
            Text(
                text = "UNLINK",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onForgetToken)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("◀ BACK" to "back", "⌂ HOME" to "home", "▢ RECENTS" to "recents").forEach { (label, key) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.2f))
                            .clickable { client.sendKeyPress(key) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (scale > 1.01f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(themeColor.copy(alpha = 0.3f))
                            .clickable { scale = 1f; panOffset = Offset.Zero }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("⊙ RESET ZOOM", color = themeColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { new ->
                    if (new.text != typed.text) client.sendKeyText(new.text)
                    typed = new
                },
                singleLine = true,
                label = { Text("Type to send text", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "1 finger: tap=tap, drag=swipe. 2 fingers: pinch=zoom, drag=pan.",
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
