package com.example.linka.core.message

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dispatches on the wire "type" field rather than relying on annotation-based
 * polymorphism, so an unrecognized type decodes to [UnknownMessage] instead of
 * throwing - the direct analog of org.json's `optString("type")` + `when` with no
 * `else` branch, which today just silently ignores anything it doesn't recognize.
 * Deserialize-only: encoding is handled explicitly by [encodeLinkMessage] below, since
 * content-polymorphic serializers don't reliably write a discriminator on the way out.
 */
object InboundMessageSerializer : JsonContentPolymorphicSerializer<LinkMessage>(LinkMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<LinkMessage> =
        when (element.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
            "tap" -> ControlCommand.Tap.serializer()
            "swipe" -> ControlCommand.Swipe.serializer()
            "mouse_move" -> ControlCommand.MouseMove.serializer()
            "mouse_click" -> ControlCommand.MouseClick.serializer()
            "mouse_down" -> ControlCommand.MouseDown.serializer()
            "mouse_up" -> ControlCommand.MouseUp.serializer()
            "scroll" -> ControlCommand.Scroll.serializer()
            "key_text" -> ControlCommand.KeyText.serializer()
            "key_press" -> ControlCommand.KeyPress.serializer()
            "controller_connected" -> PresenceEvent.ControllerConnected.serializer()
            "controller_disconnected" -> PresenceEvent.ControllerDisconnected.serializer()
            "controller_offline" -> PresenceEvent.ControllerOffline.serializer()
            "agent_connected" -> PresenceEvent.AgentConnected.serializer()
            "agent_disconnected" -> PresenceEvent.AgentDisconnected.serializer()
            "agent_offline" -> PresenceEvent.AgentOffline.serializer()
            "info" -> SessionInfo.serializer()
            "app_access" -> AppAccessState.serializer()
            "chat" -> ChatMessage.serializer()
            "location" -> LocationInfo.serializer()
            else -> UnknownMessage.serializer()
        }
}

val LinkMessageJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}

fun Json.decodeLinkMessage(text: String): LinkMessage =
    decodeFromString(InboundMessageSerializer, text)

/** Explicit type-tag + field union so the wire shape is exactly `{"type": ..., ...fields}`. */
fun Json.encodeLinkMessage(message: LinkMessage): String {
    val type: String
    val fields: JsonObject
    when (message) {
        is ControlCommand.Tap -> { type = "tap"; fields = encodeToJsonElement(ControlCommand.Tap.serializer(), message).jsonObject }
        is ControlCommand.Swipe -> { type = "swipe"; fields = encodeToJsonElement(ControlCommand.Swipe.serializer(), message).jsonObject }
        is ControlCommand.MouseMove -> { type = "mouse_move"; fields = encodeToJsonElement(ControlCommand.MouseMove.serializer(), message).jsonObject }
        is ControlCommand.MouseClick -> { type = "mouse_click"; fields = encodeToJsonElement(ControlCommand.MouseClick.serializer(), message).jsonObject }
        is ControlCommand.MouseDown -> { type = "mouse_down"; fields = encodeToJsonElement(ControlCommand.MouseDown.serializer(), message).jsonObject }
        is ControlCommand.MouseUp -> { type = "mouse_up"; fields = encodeToJsonElement(ControlCommand.MouseUp.serializer(), message).jsonObject }
        is ControlCommand.Scroll -> { type = "scroll"; fields = encodeToJsonElement(ControlCommand.Scroll.serializer(), message).jsonObject }
        is ControlCommand.KeyText -> { type = "key_text"; fields = encodeToJsonElement(ControlCommand.KeyText.serializer(), message).jsonObject }
        is ControlCommand.KeyPress -> { type = "key_press"; fields = encodeToJsonElement(ControlCommand.KeyPress.serializer(), message).jsonObject }
        is PresenceEvent.ControllerConnected -> { type = "controller_connected"; fields = JsonObject(emptyMap()) }
        is PresenceEvent.ControllerDisconnected -> { type = "controller_disconnected"; fields = JsonObject(emptyMap()) }
        is PresenceEvent.ControllerOffline -> { type = "controller_offline"; fields = JsonObject(emptyMap()) }
        is PresenceEvent.AgentConnected -> { type = "agent_connected"; fields = JsonObject(emptyMap()) }
        is PresenceEvent.AgentDisconnected -> { type = "agent_disconnected"; fields = JsonObject(emptyMap()) }
        is PresenceEvent.AgentOffline -> { type = "agent_offline"; fields = JsonObject(emptyMap()) }
        is SessionInfo -> { type = "info"; fields = encodeToJsonElement(SessionInfo.serializer(), message).jsonObject }
        is AppAccessState -> { type = "app_access"; fields = encodeToJsonElement(AppAccessState.serializer(), message).jsonObject }
        is ChatMessage -> { type = "chat"; fields = encodeToJsonElement(ChatMessage.serializer(), message).jsonObject }
        is LocationInfo -> { type = "location"; fields = encodeToJsonElement(LocationInfo.serializer(), message).jsonObject }
        is UnknownMessage -> { type = ""; fields = JsonObject(emptyMap()) }
    }
    val obj = buildJsonObject {
        put("type", JsonPrimitive(type))
        fields.forEach { (key, value) -> put(key, value) }
    }
    return obj.toString()
}
