package com.example.linka.core.socket

import com.example.linka.core.message.LinkMessage
import com.example.linka.core.message.LinkMessageJson
import com.example.linka.core.message.decodeLinkMessage
import com.example.linka.core.message.encodeLinkMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KtorControlSocketClient(
    private val url: String,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val reconnect: ReconnectPolicy? = ReconnectPolicy(),
) : ControlSocketClient {

    private val _connectionState = MutableStateFlow(SocketConnState.CLOSED)
    override val connectionState = _connectionState.asStateFlow()

    private val _incoming = MutableSharedFlow<LinkMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<LinkMessage> = _incoming

    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    override val frames: Flow<ByteArray> = _frames

    private var connectJob: Job? = null
    private var manuallyDisconnected = false

    override fun connect() {
        manuallyDisconnected = false
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        var currentDelayMs = reconnect?.initialDelayMs ?: 0L
        while (!manuallyDisconnected) {
            _connectionState.value = SocketConnState.CONNECTING
            try {
                httpClient.webSocket(url) {
                    activeSession = this
                    currentDelayMs = reconnect?.initialDelayMs ?: 0L
                    _connectionState.value = SocketConnState.OPEN
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> runCatching {
                                LinkMessageJson.decodeLinkMessage(frame.readText())
                            }.getOrNull()?.let { _incoming.emit(it) }
                            is Frame.Binary -> _frames.emit(frame.readBytes())
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection attempt failed or dropped mid-session; fall through to the
                // reconnect/stop decision below exactly the same as a clean close.
            }
            activeSession = null
            _connectionState.value = SocketConnState.CLOSED
            if (manuallyDisconnected) break
            val policy = reconnect ?: break
            delay(currentDelayMs)
            currentDelayMs = policy.nextDelay(currentDelayMs)
        }
    }

    override fun send(message: LinkMessage) {
        val text = LinkMessageJson.encodeLinkMessage(message)
        // Best-effort: matches today's clients, which don't queue sends made while the
        // socket happens to be down between reconnect attempts.
        scope.launch { runCatching { activeSession?.send(Frame.Text(text)) } }
    }

    override fun sendFrame(bytes: ByteArray) {
        scope.launch { runCatching { activeSession?.send(Frame.Binary(true, bytes)) } }
    }

    override fun disconnect() {
        manuallyDisconnected = true
        connectJob?.cancel()
        _connectionState.value = SocketConnState.CLOSED
    }

    // Ktor's websocket { } block doesn't hand back the session outside the block, so we
    // track it ourselves purely for send() to have somewhere to write to.
    private var activeSession: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null
}
