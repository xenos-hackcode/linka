package com.example.linka.core.socket

import com.example.linka.core.message.LinkMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SocketConnState { CONNECTING, OPEN, CLOSED }

/**
 * initialDelayMs -> doubling -> maxDelayMs, matching the reconnect-with-backoff both
 * controller screen files hand-roll today (2000ms -> ... -> 30000ms cap).
 */
data class ReconnectPolicy(
    val initialDelayMs: Long = 2000L,
    val maxDelayMs: Long = 30000L,
) {
    fun nextDelay(currentDelayMs: Long): Long = (currentDelayMs * 2).coerceAtMost(maxDelayMs)
}

/**
 * One control WebSocket connection: text frames decode to [LinkMessage], binary frames
 * (JPEG screen captures) pass through raw. Pass `reconnect = null` for a connection that
 * should simply end on close rather than retry - that's PhoneLinkAgentClient's existing
 * behavior today, and this refactor must not change it.
 */
interface ControlSocketClient {
    val connectionState: StateFlow<SocketConnState>
    val incoming: Flow<LinkMessage>
    val frames: Flow<ByteArray>

    fun connect()
    fun send(message: LinkMessage)
    fun sendFrame(bytes: ByteArray)
    fun disconnect()
}
