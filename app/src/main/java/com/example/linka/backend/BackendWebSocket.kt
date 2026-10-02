package com.example.linka.backend

import com.example.linka.core.socket.ReconnectPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Live push for the backend's WebSocket (see server/src/ws.ts) - global chat, group
 * chat, and domain-request updates all arrive here, on top of being fetchable via
 * REST, so an open screen updates without polling. One connection per signed-in
 * session; call connect() once authenticated, disconnect() on sign-out/teardown.
 * Reconnects automatically with backoff (2026-09-14) - reuses :core's own
 * ReconnectPolicy (2s -> doubling -> 30s cap), the same shape both controller
 * screens already use for their own control-socket reconnects, rather than a
 * second hand-rolled backoff. A dropped connection (network switch,
 * backgrounding, Cloud Run cold start) now recovers on its own; the REST calls
 * in BackendClient still work standalone regardless, this is only the
 * real-time push on top of them. */
class BackendWebSocket(private val token: String) {
    var onGlobalMessage: ((BackendGlobalMessage) -> Unit)? = null
    var onGroupMessage: ((BackendGroupMessage) -> Unit)? = null
    var onRequestCreated: ((BackendRequest) -> Unit)? = null
    var onRequestUpdated: ((BackendRequest) -> Unit)? = null
    var onDmMessage: ((BackendDmMessage) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(OkHttp) { install(WebSockets) }
    private val json = Json { ignoreUnknownKeys = true }
    private val reconnect = ReconnectPolicy()

    private var connectJob: Job? = null
    private var manuallyDisconnected = false

    fun connect() {
        manuallyDisconnected = false
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        var currentDelayMs = reconnect.initialDelayMs
        while (!manuallyDisconnected) {
            runCatching {
                client.webSocket(urlString = "wss://$WS_HOST/ws?token=$token") {
                    // A real connection was established - reset the backoff so the
                    // NEXT drop (not this one) starts fresh at initialDelayMs again,
                    // rather than a transient blip permanently inheriting whatever
                    // delay a much earlier, longer outage had backed off to.
                    currentDelayMs = reconnect.initialDelayMs
                    for (frame in incoming) {
                        if (frame is Frame.Text) handle(frame.readText())
                    }
                }
            }
            // Lands here whether the block above threw (connection never opened,
            // or dropped mid-session) or returned cleanly (the for-loop over
            // `incoming` just ended because the server closed it) - either way
            // the socket is gone and worth retrying with the same backoff.
            if (manuallyDisconnected) break
            delay(currentDelayMs)
            currentDelayMs = reconnect.nextDelay(currentDelayMs)
        }
    }

    private fun handle(text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "global_message" -> decode<BackendGlobalMessage>(obj["message"])?.let { onGlobalMessage?.invoke(it) }
            "group_message" -> decode<BackendGroupMessage>(obj["message"])?.let { onGroupMessage?.invoke(it) }
            "request_created" -> decode<BackendRequest>(obj["request"])?.let { onRequestCreated?.invoke(it) }
            "request_updated" -> decode<BackendRequest>(obj["request"])?.let { onRequestUpdated?.invoke(it) }
            "dm_message" -> decode<BackendDmMessage>(obj["message"])?.let { onDmMessage?.invoke(it) }
        }
    }

    private inline fun <reified T> decode(element: kotlinx.serialization.json.JsonElement?): T? =
        element?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }

    fun disconnect() {
        manuallyDisconnected = true
        connectJob?.cancel()
        scope.cancel()
        client.close()
    }
}
