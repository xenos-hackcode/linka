package com.example.linka.core.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LinkMessageSerializationTest {

    private fun decode(text: String): LinkMessage = LinkMessageJson.decodeLinkMessage(text)

    @Test
    fun decodesTap() {
        assertEquals(ControlCommand.Tap(0.5, 0.5), decode("""{"type":"tap","x":0.5,"y":0.5}"""))
    }

    @Test
    fun decodesSwipeWithExplicitDuration() {
        assertEquals(
            ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, 900L),
            decode("""{"type":"swipe","x1":0.1,"y1":0.2,"x2":0.3,"y2":0.4,"durationMs":900}"""),
        )
    }

    @Test
    fun swipeWithoutDurationDefaultsTo250() {
        val message = decode("""{"type":"swipe","x1":0.1,"y1":0.2,"x2":0.3,"y2":0.4}""")
        assertEquals(ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, 250L), message)
    }

    @Test
    fun decodesMouseMove() {
        assertEquals(ControlCommand.MouseMove(0.5, 0.5), decode("""{"type":"mouse_move","x":0.5,"y":0.5}"""))
    }

    @Test
    fun decodesMouseClickWithButton() {
        assertEquals(
            ControlCommand.MouseClick(0.5, 0.5, "right"),
            decode("""{"type":"mouse_click","x":0.5,"y":0.5,"button":"right"}"""),
        )
    }

    @Test
    fun mouseClickWithoutButtonDefaultsToLeft() {
        assertEquals(
            ControlCommand.MouseClick(0.5, 0.5, "left"),
            decode("""{"type":"mouse_click","x":0.5,"y":0.5}"""),
        )
    }

    @Test
    fun decodesMouseDownAndUp() {
        assertEquals(ControlCommand.MouseDown(0.2, 0.3), decode("""{"type":"mouse_down","x":0.2,"y":0.3}"""))
        assertEquals(ControlCommand.MouseUp(0.2, 0.3), decode("""{"type":"mouse_up","x":0.2,"y":0.3}"""))
    }

    @Test
    fun decodesScroll() {
        assertEquals(ControlCommand.Scroll(-120), decode("""{"type":"scroll","dy":-120}"""))
    }

    @Test
    fun decodesKeyTextAndKeyPress() {
        assertEquals(ControlCommand.KeyText("hello"), decode("""{"type":"key_text","text":"hello"}"""))
        assertEquals(ControlCommand.KeyPress("back"), decode("""{"type":"key_press","key":"back"}"""))
    }

    @Test
    fun decodesAllPresenceEvents() {
        assertEquals(PresenceEvent.ControllerConnected, decode("""{"type":"controller_connected"}"""))
        assertEquals(PresenceEvent.ControllerDisconnected, decode("""{"type":"controller_disconnected"}"""))
        assertEquals(PresenceEvent.ControllerOffline, decode("""{"type":"controller_offline"}"""))
        assertEquals(PresenceEvent.AgentConnected, decode("""{"type":"agent_connected"}"""))
        assertEquals(PresenceEvent.AgentDisconnected, decode("""{"type":"agent_disconnected"}"""))
        assertEquals(PresenceEvent.AgentOffline, decode("""{"type":"agent_offline"}"""))
    }

    @Test
    fun decodesSessionInfo() {
        assertEquals(SessionInfo(1920, 1080), decode("""{"type":"info","width":1920,"height":1080}"""))
    }

    @Test
    fun decodesAppAccessState() {
        assertEquals(AppAccessState(true), decode("""{"type":"app_access","allowed":true}"""))
        assertEquals(AppAccessState(false), decode("""{"type":"app_access","allowed":false}"""))
    }

    @Test
    fun decodesChatMessage() {
        assertEquals(ChatMessage("hi there"), decode("""{"type":"chat","text":"hi there"}"""))
    }

    @Test
    fun decodesLocationInfo() {
        assertEquals(LocationInfo("Kenya"), decode("""{"type":"location","country":"Kenya"}"""))
    }

    @Test
    fun unrecognizedTypeDecodesToUnknownMessageWithoutThrowing() {
        assertIs<UnknownMessage>(decode("""{"type":"future_thing","x":1}"""))
    }

    @Test
    fun missingTypeFieldDecodesToUnknownMessageWithoutThrowing() {
        assertIs<UnknownMessage>(decode("""{"x":1}"""))
    }

    @Test
    fun extraUnknownFieldsAreIgnored() {
        assertEquals(
            ControlCommand.Tap(0.5, 0.5),
            decode("""{"type":"tap","x":0.5,"y":0.5,"someFutureField":"whatever"}"""),
        )
    }

    @Test
    fun encodeThenDecodeRoundTripsForEveryVariant() {
        val samples: List<LinkMessage> = listOf(
            ControlCommand.Tap(0.12, 0.34),
            ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, 500L),
            ControlCommand.MouseMove(0.5, 0.6),
            ControlCommand.MouseClick(0.5, 0.6, "middle"),
            ControlCommand.MouseDown(0.1, 0.1),
            ControlCommand.MouseUp(0.1, 0.1),
            ControlCommand.Scroll(42),
            ControlCommand.KeyText("hello world"),
            ControlCommand.KeyPress("recents"),
            PresenceEvent.ControllerConnected,
            PresenceEvent.ControllerDisconnected,
            PresenceEvent.ControllerOffline,
            PresenceEvent.AgentConnected,
            PresenceEvent.AgentDisconnected,
            PresenceEvent.AgentOffline,
            SessionInfo(1280, 720),
            AppAccessState(true),
            AppAccessState(false),
            ChatMessage("hello"),
            LocationInfo("Nigeria"),
        )
        for (sample in samples) {
            val encoded = LinkMessageJson.encodeLinkMessage(sample)
            val decoded = decode(encoded)
            assertEquals(sample, decoded, "round-trip failed for $sample -> $encoded")
        }
    }
}
