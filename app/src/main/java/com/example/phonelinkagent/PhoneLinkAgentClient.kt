package com.example.phonelinkagent

import com.example.linka.core.message.ChatMessage
import com.example.linka.core.message.ControlCommand
import com.example.linka.core.message.LinkMessage
import com.example.linka.core.message.LocationInfo
import com.example.linka.core.message.PresenceEvent
import com.example.linka.core.socket.KtorControlSocketClient
import com.example.linka.core.socket.SocketConnState
import com.example.linka.core.socket.buildControlHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Phone side of the Link to Phone relay's agent role - the phone being controlled. Wire
 * protocol and connection handling now live in :core's KtorControlSocketClient/LinkMessage;
 * this wraps it with the same public surface PhoneLinkAccessibilityService already used.
 * Deliberately passes no ReconnectPolicy to KtorControlSocketClient - unlike the controller
 * screens, this class never retried on its own; a closed socket just ends the session
 * (see onClosed below, and PhoneLinkAccessibilityService's handling of it). */
class PhoneLinkAgentClient(private val token: String) {
    var onControllerPresence: ((Boolean) -> Unit)? = null
    var onCommand: ((ControlCommand) -> Unit)? = null
    var onChatMessage: ((String) -> Unit)? = null
    var onLocationReceived: ((String) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val socket = KtorControlSocketClient(
        url = "wss://elene-backend-717899371194.us-central1.run.app/phone/ws/agent/$token",
        httpClient = buildControlHttpClient(),
        scope = scope,
        reconnect = null,
    )

    fun connect() {
        android.util.Log.d("PhoneLinkAgentClient", "connecting: token=$token")
        scope.launch {
            // drop(1): skip the socket's initial pre-connect CLOSED placeholder - only a
            // real close after having been open should fire onClosed.
            socket.connectionState.drop(1).collect { state ->
                if (state == SocketConnState.CLOSED) {
                    android.util.Log.w("PhoneLinkAgentClient", "WS closed")
                    onClosed?.invoke()
                }
            }
        }
        scope.launch {
            socket.incoming.collect { message ->
                when (message) {
                    is PresenceEvent.ControllerConnected -> onControllerPresence?.invoke(true)
                    is PresenceEvent.ControllerDisconnected, is PresenceEvent.ControllerOffline ->
                        onControllerPresence?.invoke(false)
                    is ControlCommand -> onCommand?.invoke(message)
                    is ChatMessage -> onChatMessage?.invoke(message.text)
                    is LocationInfo -> onLocationReceived?.invoke(message.country)
                    else -> Unit
                }
            }
        }
        socket.connect()
    }

    fun sendFrame(bytes: ByteArray) {
        socket.sendFrame(bytes)
    }

    fun send(message: LinkMessage) {
        socket.send(message)
    }

    fun disconnect() {
        socket.disconnect()
        scope.cancel()
    }
}
