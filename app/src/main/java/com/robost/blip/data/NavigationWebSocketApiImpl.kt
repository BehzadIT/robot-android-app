package com.robost.blip.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class NavigationWebSocketApiImpl(
    private val client: HttpClient
) : NavigationWebSocketApi {
    private val wsUrl: String = "ws://10.0.0.186/ws"
    private var wsSession: DefaultClientWebSocketSession? = null
    private var wsJob: Job? = null

    override fun connect(
        onMessage: (String) -> Unit,
        onClose: () -> Unit
    ) {
        wsJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                client.webSocket(urlString = wsUrl) {
                    wsSession = this
                    println("WebSocket connected")
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val msg = frame.readText()
                                withContext(Dispatchers.Main) {
                                    onMessage(msg)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("WebSocket error: ${e.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                println("WebSocket failed: ${e.localizedMessage}")
            } finally {
                wsSession = null
                withContext(Dispatchers.Main) { onClose() }
            }
        }
    }

    override suspend fun sendDriveCommand(targetRpm: Int, targetDirection: Int) {
        val json = Json.encodeToString(
            mapOf(
                "type" to "drive",
                "target_rpm" to targetRpm.toString(),
                "target_direction" to targetDirection.toString()
            )
        )
        wsSession?.send(Frame.Text(json))
    }

    override suspend fun sendStopCommand() {
        val json = Json.encodeToString(mapOf("type" to "stop"))
        wsSession?.send(Frame.Text(json))
    }

    override fun disconnect() {
        wsJob?.cancel()
        wsSession = null
    }
}

interface NavigationWebSocketApi {
    fun connect(onMessage: (String) -> Unit = {}, onClose: () -> Unit = {})
    suspend fun sendDriveCommand(targetRpm: Int, targetDirection: Int)
    suspend fun sendStopCommand()
    fun disconnect()
}
