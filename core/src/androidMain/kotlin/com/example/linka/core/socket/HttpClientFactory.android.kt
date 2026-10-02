package com.example.linka.core.socket

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import java.util.concurrent.TimeUnit

actual fun buildControlHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    engine {
        config {
            // Matches the ping interval both existing OkHttp-based clients set today.
            pingInterval(20, TimeUnit.SECONDS)
        }
    }
}
