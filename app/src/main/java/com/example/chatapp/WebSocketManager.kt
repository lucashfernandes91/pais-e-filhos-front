package com.example.chatapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class WsStatus {
    CONNECTED, DISCONNECTED, RECONNECTING, ERROR
}

data class IncomingMessage(
    val messageId: Int,
    val content: String,
    val senderId: Int,
    val senderUsername: String,
    val createdAt: String,
    val clientMessageId: String?
)

class WebSocketManager(
    private val conversationId: Int,
    private val token: String,
    val onMessageReceived: (IncomingMessage) -> Unit,
    val onConnected: () -> Unit,
    val onDisconnected: () -> Unit,
    val onStatusChanged: (WsStatus) -> Unit,
    val onTypingReceived: (String, Boolean) -> Unit = { _, _ -> }
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private val correlationId = AppTelemetry.newCorrelationId()
    private val maxReconnectAttempts = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun connect() {
        scope.launch {
            // Token via header: não vaza em logs de servidor/proxy (C8).
            val url = "${BuildConfig.WS_BASE_URL}ws/chat/$conversationId/"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            webSocket = client.newWebSocket(request, ChatWebSocketListener())
        }
    }

    fun sendMessage(content: String, clientMessageId: String): Boolean {
        val socket = webSocket ?: run {
            AppTelemetry.warning("websocket_send_not_connected", correlationId)
            onStatusChanged(WsStatus.DISCONNECTED)
            return false
        }
        val sent = socket.send(
            JSONObject()
                .put("content", content)
                .put("client_message_id", clientMessageId)
                .toString()
        )
        if (!sent) {
            AppTelemetry.error("websocket_send_failed", correlationId)
            onStatusChanged(WsStatus.ERROR)
        }
        return sent
    }

    fun sendTyping(isTyping: Boolean) {
        scope.launch {
            webSocket?.send(
                JSONObject()
                    .put("type", "typing")
                    .put("is_typing", isTyping)
                    .toString()
            )
        }
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
        reconnectAttempts = 0
        scope.cancel()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            AppTelemetry.error(
                "websocket_reconnect_exhausted",
                correlationId,
                attributes = mapOf("attempts" to reconnectAttempts.toString())
            )
            onStatusChanged(WsStatus.ERROR)
            return
        }

        reconnectAttempts++
        val delaySeconds = minOf(2.0.pow(reconnectAttempts.toDouble()).toLong(), 30L)

        scope.launch {
            onStatusChanged(WsStatus.RECONNECTING)
            delay(delaySeconds * 1000)
            connect()
        }
    }

    private inner class ChatWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            AppTelemetry.info("websocket_connected", correlationId)
            reconnectAttempts = 0
            onStatusChanged(WsStatus.CONNECTED)
            onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = android.util.JsonReader(text.reader())
                json.beginObject()

                var messageId = 0
                var content = ""
                var senderId = 0
                var senderUsername = ""
                var createdAt = ""
                var clientMessageId: String? = null
                var type = "message"
                var isTyping = false

                while (json.hasNext()) {
                    when (json.nextName()) {
                        "type" -> type = json.nextString()
                        "message_id" -> messageId = json.nextInt()
                        "content" -> content = json.nextString()
                        "sender_id" -> senderId = json.nextInt()
                        "sender_username" -> senderUsername = json.nextString()
                        "created_at" -> createdAt = json.nextString()
                        "client_message_id" -> clientMessageId = json.nextString()
                        "is_typing" -> isTyping = json.nextBoolean()
                        else -> json.skipValue()
                    }
                }
                json.endObject()

                if (type == "typing") {
                    AppTelemetry.debug(TAG, "Typing event received")
                    onTypingReceived(senderUsername, isTyping)
                } else {
                    AppTelemetry.info("websocket_message_received", correlationId)
                    val message = IncomingMessage(
                        messageId,
                        content,
                        senderId,
                        senderUsername,
                        createdAt,
                        clientMessageId
                    )
                    onMessageReceived(message)
                }
            } catch (e: Exception) {
                AppTelemetry.error("websocket_message_parse_failed", correlationId, e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            AppTelemetry.error("websocket_failure", correlationId, t)
            onStatusChanged(WsStatus.DISCONNECTED)
            scheduleReconnect()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            AppTelemetry.debug(TAG, "WebSocket closing: $code")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            AppTelemetry.info(
                "websocket_closed",
                correlationId,
                attributes = mapOf("code" to code.toString())
            )
            onStatusChanged(WsStatus.DISCONNECTED)
            if (code != 1000 && code != 4001 && code != 4003) {
                scheduleReconnect()
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketManager"
    }
}

private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
