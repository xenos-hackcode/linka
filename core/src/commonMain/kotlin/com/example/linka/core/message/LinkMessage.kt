package com.example.linka.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every text-frame message exchanged over the control WebSocket, for both the
 * phone-target and laptop-target protocols. One hierarchy, not two: key_text/key_press
 * and the presence events are already identical across both wire protocols, and the
 * `/phone/ws/...` vs `/laptop/ws/...` route already enforces the real separation - a
 * receiver's `when` just ignores subtypes it doesn't understand.
 */
sealed interface LinkMessage

sealed interface ControlCommand : LinkMessage {
    @Serializable
    @SerialName("tap")
    data class Tap(val x: Double, val y: Double) : ControlCommand

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val durationMs: Long = 250L,
    ) : ControlCommand

    @Serializable
    @SerialName("mouse_move")
    data class MouseMove(val x: Double, val y: Double) : ControlCommand

    @Serializable
    @SerialName("mouse_click")
    data class MouseClick(val x: Double, val y: Double, val button: String = "left") : ControlCommand

    // Modeled to match what laptop_agent/agent.py already expects on the wire; not sent
    // by any Compose UI yet (that's a separate, later feature - see PLANNING.md).
    @Serializable
    @SerialName("mouse_down")
    data class MouseDown(val x: Double, val y: Double) : ControlCommand

    @Serializable
    @SerialName("mouse_up")
    data class MouseUp(val x: Double, val y: Double) : ControlCommand

    @Serializable
    @SerialName("scroll")
    data class Scroll(val dy: Int) : ControlCommand

    @Serializable
    @SerialName("key_text")
    data class KeyText(val text: String) : ControlCommand

    @Serializable
    @SerialName("key_press")
    data class KeyPress(val key: String) : ControlCommand
}

sealed interface PresenceEvent : LinkMessage {
    @Serializable
    @SerialName("controller_connected")
    data object ControllerConnected : PresenceEvent

    @Serializable
    @SerialName("controller_disconnected")
    data object ControllerDisconnected : PresenceEvent

    @Serializable
    @SerialName("controller_offline")
    data object ControllerOffline : PresenceEvent

    @Serializable
    @SerialName("agent_connected")
    data object AgentConnected : PresenceEvent

    @Serializable
    @SerialName("agent_disconnected")
    data object AgentDisconnected : PresenceEvent

    @Serializable
    @SerialName("agent_offline")
    data object AgentOffline : PresenceEvent
}

@Serializable
@SerialName("info")
data class SessionInfo(val width: Int, val height: Int) : LinkMessage

/**
 * Sent controllee -> controller whenever the controllee's foreground app allowlist
 * state changes (see PLANNING.md §3.1). Deliberately carries no app name/package -
 * the controller only learns blocked-or-not, not what's running, matching the
 * controllee-protection framing used throughout the rest of the app.
 */
@Serializable
@SerialName("app_access")
data class AppAccessState(val allowed: Boolean) : LinkMessage

/**
 * Paired chat (PLANNING.md §3.3) - controller <-> their own controllee, over the same
 * socket already open between them. Bidirectional; no sender field needed since each
 * side already knows locally-sent vs. received by which code path added it.
 */
@Serializable
@SerialName("chat")
data class ChatMessage(val text: String) : LinkMessage

/**
 * Best-effort country exchange for the activity log (see ActivityLogEntry.kt in the
 * app module) - sent by each side independently once a session is live, so each side
 * logs whatever it receives as "the other person's" country. Not sent at all if the
 * sender's own location permission was denied or lookup failed - absence just means
 * "unknown" on the receiving side, never blocks anything.
 */
@Serializable
@SerialName("location")
data class LocationInfo(val country: String) : LinkMessage

/** Anything with an unrecognized `type` - mirrors org.json's silent no-op today. */
@Serializable
data object UnknownMessage : LinkMessage
