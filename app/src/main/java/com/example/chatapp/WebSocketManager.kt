package com.example.chatapp

import android.util.Log
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
import java.util.concurrent.TimeUnit

enum class WsStatus {
    CONNECTED, DISCONNECTED, RECONNECTING, ERROR
}

data class IncomingMessage(
    val messageId: Int,
    val content: String,
    val senderId: Int,
    val senderUsername: String,
    val createdAt: String
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
    private val maxReconnectAttempts = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun connect() {
        scope.launch {
            val url = "ws://10.0.2.2:8000/ws/chat/$conversationId/?token=$token"
            val request = Request.Builder().url(url).build()

            webSocket = client.newWebSocket(request, ChatWebSocketListener())
        }
    }

    fun sendMessage(content: String) {
        scope.launch {
            if (webSocket != null) {
                val json = """{"content":"$content"}"""
                val sent = webSocket!!.send(json)
                if (!sent) {
                    Log.e(TAG, "Failed to send message")
                    onStatusChanged(WsStatus.ERROR)
                }
            } else {
                Log.w(TAG, "WebSocket not connected")
                onStatusChanged(WsStatus.DISCONNECTED)
            }
        }
    }

    fun sendTyping(isTyping: Boolean) {
        scope.launch {
            webSocket?.send("""{"type":"typing","is_typing":$isTyping}""")
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
            Log.e(TAG, "Max reconnect attempts reached")
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
            Log.d(TAG, "WebSocket connected")
            reconnectAttempts = 0
            onStatusChanged(WsStatus.CONNECTED)
            onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Message received: $text")
            try {
                val json = android.util.JsonReader(text.reader())
                json.beginObject()

                var messageId = 0
                var content = ""
                var senderId = 0
                var senderUsername = ""
                var createdAt = ""
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
                        "is_typing" -> isTyping = json.nextBoolean()
                        else -> json.skipValue()
                    }
                }
                json.endObject()

                if (type == "typing") {
                    onTypingReceived(senderUsername, isTyping)
                } else {
                    val message = IncomingMessage(messageId, content, senderId, senderUsername, createdAt)
                    onMessageReceived(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            Log.e(TAG, "WebSocket error: ${t.message}", t)
            onStatusChanged(WsStatus.DISCONNECTED)
            scheduleReconnect()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
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
