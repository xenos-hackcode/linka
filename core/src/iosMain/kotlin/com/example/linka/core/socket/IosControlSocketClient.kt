package com.example.linka.core.socket

import com.example.linka.core.message.ControlCommand
import com.example.linka.core.message.PresenceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Swift-friendly facade over KtorControlSocketClient, for the iOS Controller role only
 * (no agent/controllee-facing messages are handled here). Deliberately does NOT expose
 * the sealed LinkMessage/ControlCommand/PresenceEvent hierarchy across the Kotlin/
 * Native Objective-C boundary - nested sealed classes export to Swift in a way that's
 * easy to get wrong without a real build to check against. All pattern matching on
 * message types happens here in Kotlin (the same `when` shape used elsewhere in this
 * codebase); only primitives and the plain SocketConnState enum cross into Swift -
 * both are the well-documented, reliable part of Kotlin/Native's Swift export.
 *
 * Mirrors what the Android controller screens' wrapper clients (LaptopControlClient/
 * PhoneControlClient) already do for their platform: typed send methods, typed
 * callbacks, no raw message objects handed to the caller.
 */
class IosControlSocketClient(private val url: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val socket: ControlSocketClient = KtorControlSocketClient(
        url = url,
        httpClient = buildControlHttpClient(),
        scope = scope,
        reconnect = ReconnectPolicy(),
    )

    // ---- Inbound - assign before calling connect() ----
    var onConnectionStateChanged: ((SocketConnState) -> Unit)? = null
    var onAgentConnected: (() -> Unit)? = null
    var onAgentDisconnected: (() -> Unit)? = null
    var onFrame: ((ByteArray) -> Unit)? = null

    fun connect() {
        scope.launch {
            socket.connectionState.collect { onConnectionStateChanged?.invoke(it) }
        }
        scope.launch {
            socket.incoming.collect { message ->
                when (message) {
                    is PresenceEvent.AgentConnected -> onAgentConnected?.invoke()
                    is PresenceEvent.AgentDisconnected, is PresenceEvent.AgentOffline ->
                        onAgentDisconnected?.invoke()
                    // Controller-side socket never receives controller_*/info/ControlCommand
                    // on this route - nothing else to forward.
                    else -> Unit
                }
            }
        }
        scope.launch {
            socket.frames.collect { onFrame?.invoke(it) }
        }
        socket.connect()
    }

    // ---- Outbound ----
    fun sendTap(x: Double, y: Double) = socket.send(ControlCommand.Tap(x, y))

    fun sendSwipe(x1: Double, y1: Double, x2: Double, y2: Double, durationMs: Long) =
        socket.send(ControlCommand.Swipe(x1, y1, x2, y2, durationMs))

    fun sendMouseMove(x: Double, y: Double) = socket.send(ControlCommand.MouseMove(x, y))

    fun sendMouseClick(x: Double, y: Double, button: String) =
        socket.send(ControlCommand.MouseClick(x, y, button))

    fun sendScroll(dy: Int) = socket.send(ControlCommand.Scroll(dy))

    fun sendKeyText(text: String) = socket.send(ControlCommand.KeyText(text))

    fun sendKeyPress(key: String) = socket.send(ControlCommand.KeyPress(key))

    fun disconnect() {
        socket.disconnect()
        scope.cancel()
    }
}
