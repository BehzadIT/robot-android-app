package com.bizi.roboapp.core.protocol.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DriveRequest(
    @SerialName("type") val type: String,
    @SerialName("target_rpm") val targetRpm: Int,
    @SerialName("target_direction") val targetDirection: Int,
    @SerialName("target_angle") val targetAngle: Int
)

@Serializable
data class DriveCommandPayload(
    @SerialName("t") val type: String,
    @SerialName("r") val targetRpm: Int,
    @SerialName("d") val targetDirection: Int,
    @SerialName("a") val targetAngle: Int,
    @SerialName("s") val seq: Long,
    @SerialName("m") val sentAtMs: Long
)

@Serializable
data class StopCommandPayload(
    @SerialName("t") val type: String,
    @SerialName("s") val seq: Long,
    @SerialName("m") val sentAtMs: Long
)

@Serializable
data class RecoverCommandPayload(
    @SerialName("t") val type: String,
    @SerialName("s") val seq: Long,
    @SerialName("m") val sentAtMs: Long
)

sealed interface NavigationServerEvent {
    data class Ack(val code: String?, val seq: Long?) : NavigationServerEvent
    data class Error(val code: String?, val seq: Long?, val message: String?) : NavigationServerEvent
}

private val protocolJson = Json {
    ignoreUnknownKeys = true
}

fun parseNavigationServerEvent(message: String): NavigationServerEvent? {
    val payload = try {
        protocolJson.parseToJsonElement(message).jsonObject
    } catch (_: Exception) {
        return null
    }

    val seq = payload["s"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    return when (payload["t"]?.jsonPrimitive?.contentOrNull) {
        "a" -> NavigationServerEvent.Ack(
            code = payload["c"]?.jsonPrimitive?.contentOrNull,
            seq = seq
        )
        "e" -> NavigationServerEvent.Error(
            code = payload["c"]?.jsonPrimitive?.contentOrNull,
            seq = seq,
            message = payload["m"]?.jsonPrimitive?.contentOrNull
        )
        else -> null
    }
}

fun summarizeNavigationServerEvent(message: String): String {
    return when (val event = parseNavigationServerEvent(message)) {
        is NavigationServerEvent.Ack -> "ack code=${event.code} seq=${event.seq}"
        is NavigationServerEvent.Error -> "error code=${event.code}"
        null -> message.take(120)
    }
}
