package com.example.linka.core.socket

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KtorControlSocketClient's reconnect loop delegates every delay decision to
 * ReconnectPolicy.nextDelay - testing it directly here exercises the exact logic
 * production code runs, without needing to fake a WebSocket handshake through an
 * engine mock just to observe timing.
 */
class ReconnectPolicyTest {

    @Test
    fun delaySequenceDoublesThenCapsAtMax() {
        val policy = ReconnectPolicy(initialDelayMs = 2000L, maxDelayMs = 30000L)
        var delayMs = policy.initialDelayMs
        val observed = mutableListOf(delayMs)
        repeat(6) {
            delayMs = policy.nextDelay(delayMs)
            observed += delayMs
        }
        assertEquals(listOf(2000L, 4000L, 8000L, 16000L, 30000L, 30000L, 30000L), observed)
    }
}
