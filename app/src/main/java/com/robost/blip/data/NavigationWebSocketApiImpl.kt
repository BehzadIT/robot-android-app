package com.robost.blip.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "NavigationWebSocket"
private const val EVENT_BUFFER_SIZE = 40
private const val STABLE_CONNECTION_RESET_MS = 15_000L
private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
private const val MAX_RECONNECT_DELAY_MS = 15_000L

class NavigationWebSocketApiImpl(
    private val client: HttpClient
) : NavigationWebSocketApi {
    private val wsUrl = "ws://10.0.0.186/ws"
    private val apiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicLong(0)
    private val sessionMutex = Mutex()
    private val eventLock = Any()
    private val eventBuffer = ArrayDeque<ReliabilityEvent>(EVENT_BUFFER_SIZE)

    @Volatile
    private var keepRunning = false

    @Volatile
    private var wsSession: DefaultClientWebSocketSession? = null

    private var wsJob: Job? = null

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<ReliabilityEvent>>(emptyList())
    override val recentEvents: StateFlow<List<ReliabilityEvent>> = _recentEvents.asStateFlow()

    override fun connect(
        onMessage: (String) -> Unit,
        onClose: () -> Unit
    ) {
        if (wsJob?.isActive == true) {
            logEvent("connect_ignored", "connect() called while manager already active")
            return
        }

        keepRunning = true
        wsJob = apiScope.launch {
            connectionLoop(onMessage = onMessage, onClose = onClose)
        }
    }

    override suspend fun sendDriveCommand(targetRpm: Int, targetDirection: Int): Boolean {
        val drivePayload = DriveCommandPayload(
            type = "drive",
            targetRpm = targetRpm,
            targetDirection = targetDirection,
            seq = sequence.incrementAndGet(),
            sentAtMs = System.currentTimeMillis()
        )
        return sendSerializedCommand(
            payload = drivePayload,
            description = "drive seq=${drivePayload.seq} rpm=$targetRpm dir=$targetDirection"
        )
    }

    override suspend fun sendStopCommand(): Boolean {
        val stopPayload = StopCommandPayload(
            type = "stop",
            seq = sequence.incrementAndGet(),
            sentAtMs = System.currentTimeMillis()
        )
        return sendSerializedCommand(
            payload = stopPayload,
            description = "stop seq=${stopPayload.seq}"
        )
    }

    override fun disconnect() {
        keepRunning = false
        apiScope.launch {
            wsJob?.cancelAndJoin()
            sessionMutex.withLock {
                wsSession = null
            }
            _connectionState.value = WebSocketConnectionState.Disconnected
            logEvent("manager_stopped", "disconnect() called")
        }
    }

    private suspend fun connectionLoop(
        onMessage: (String) -> Unit,
        onClose: () -> Unit
    ) {
        var attempt = 0
        var lastConnectedAt = 0L

        while (keepRunning) {
            val reconnecting = attempt > 0
            _connectionState.value = if (reconnecting) {
                WebSocketConnectionState.Reconnecting(attempt)
            } else {
                WebSocketConnectionState.Connecting
            }

            logEvent(
                type = if (reconnecting) "reconnect_attempt" else "connect_attempt",
                message = "Opening websocket to $wsUrl"
            )

            try {
                client.webSocket(urlString = wsUrl) {
                    sessionMutex.withLock {
                        wsSession = this
                    }
                    lastConnectedAt = System.currentTimeMillis()
                    attempt = 0
                    _connectionState.value = WebSocketConnectionState.Connected
                    logEvent("connected", "WebSocket connected")

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val msg = frame.readText()
                                logEvent("message_received", msg.take(120))
                                onMessage(msg)
                            }
                            is Frame.Close -> {
                                logEvent("close_frame", "Server sent close frame")
                            }
                            else -> {
                                logEvent("frame_ignored", "Ignoring frame type ${frame.frameType}")
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                logEvent("manager_cancelled", "Connection manager cancelled")
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.localizedMessage ?: failure::class.simpleName.orEmpty()
                _connectionState.value = WebSocketConnectionState.Failed(message)
                logEvent("connection_failure", message)
            } finally {
                sessionMutex.withLock {
                    wsSession = null
                }
                onClose()
            }

            if (!keepRunning) {
                break
            }

            val connectedForMs = System.currentTimeMillis() - lastConnectedAt
            if (connectedForMs >= STABLE_CONNECTION_RESET_MS) {
                attempt = 0
            } else {
                attempt += 1
            }

            _connectionState.value = WebSocketConnectionState.Reconnecting(attempt)
            val delayMs = backoffDelayMs(attempt)
            logEvent("reconnect_wait", "Waiting ${delayMs}ms before retry")
            delay(delayMs)
        }

        _connectionState.value = WebSocketConnectionState.Disconnected
    }

    private fun backoffDelayMs(attempt: Int): Long {
        val exponential = INITIAL_RECONNECT_DELAY_MS * (1L shl min(attempt, 4))
        val capped = min(exponential, MAX_RECONNECT_DELAY_MS)
        return capped + Random.nextLong(0, 500)
    }

    private suspend fun sendSerializedCommand(
        payload: Any,
        description: String
    ): Boolean {
        val session = sessionMutex.withLock { wsSession }
        if (session == null) {
            logEvent("send_rejected", "$description while disconnected")
            return false
        }

        return try {
            val body = when (payload) {
                is DriveCommandPayload -> Json.encodeToString(payload)
                is StopCommandPayload -> Json.encodeToString(payload)
                else -> error("Unsupported command payload")
            }
            session.send(Frame.Text(body))
            logEvent("send_success", description)
            true
        } catch (failure: Exception) {
            logEvent(
                "send_failure",
                "$description failed: ${failure.localizedMessage ?: failure::class.simpleName}"
            )
            false
        }
    }

    private fun logEvent(type: String, message: String) {
        val event = ReliabilityEvent(
            timestampMs = System.currentTimeMillis(),
            type = type,
            message = message
        )
        synchronized(eventLock) {
            if (eventBuffer.size == EVENT_BUFFER_SIZE) {
                eventBuffer.removeFirst()
            }
            eventBuffer.addLast(event)
            _recentEvents.value = eventBuffer.toList()
        }
        Log.i(TAG, "[$type] $message")
    }
}

interface NavigationWebSocketApi {
    val connectionState: StateFlow<WebSocketConnectionState>
    val recentEvents: StateFlow<List<ReliabilityEvent>>

    fun connect(onMessage: (String) -> Unit = {}, onClose: () -> Unit = {})
    suspend fun sendDriveCommand(targetRpm: Int, targetDirection: Int): Boolean
    suspend fun sendStopCommand(): Boolean
    fun disconnect()
}

sealed interface WebSocketConnectionState {
    data object Disconnected : WebSocketConnectionState
    data object Connecting : WebSocketConnectionState
    data object Connected : WebSocketConnectionState
    data class Reconnecting(val attempt: Int) : WebSocketConnectionState
    data class Failed(val reason: String) : WebSocketConnectionState
}

data class ReliabilityEvent(
    val timestampMs: Long,
    val type: String,
    val message: String
)

@Serializable
private data class DriveCommandPayload(
    @SerialName("type") val type: String,
    @SerialName("target_rpm") val targetRpm: Int,
    @SerialName("target_direction") val targetDirection: Int,
    @SerialName("seq") val seq: Long,
    @SerialName("sent_at_ms") val sentAtMs: Long
)

@Serializable
private data class StopCommandPayload(
    @SerialName("type") val type: String,
    @SerialName("seq") val seq: Long,
    @SerialName("sent_at_ms") val sentAtMs: Long
)
