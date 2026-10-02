package com.example.linka.core.socket

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.time.Duration.Companion.seconds

/** Darwin's OkHttp-equivalent ping knob lives on the WebSockets plugin itself, not the
 * engine builder (the OkHttp-specific engine { config { pingInterval(...) } } used in
 * the Android actual doesn't apply here) - see HttpClientFactory.android.kt for the
 * Android side of this same 20s interval. */
actual fun buildControlHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets) {
        pingInterval = 20.seconds
    }
}
