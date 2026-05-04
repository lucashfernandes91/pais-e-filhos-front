package com.example.chatapp

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

class ChatViewModel(
    private val context: Context,
    private val conversationId: Int
) : ViewModel() {

    val messages = MutableLiveData<List<Message>>()
    val isLoading = MutableLiveData<Boolean>()
    val error = MutableLiveData<String?>()
    val wsStatus = MutableLiveData<WsStatus>()
    val typingUser = MutableLiveData<String?>(null)

    private val messageList = mutableListOf<Message>()
    private var token: String = ""
    private lateinit var wsManager: WebSocketManager
    private val currentUsername by lazy { PrefsHelper.getUsername(context) }

    // Item 19: Offline queue
    private val pendingMessages = mutableListOf<String>()

    init {
        loadToken()
        initWebSocket()
    }

    private fun loadToken() {
        token = PrefsHelper.getAuthToken(context)
        if (token.isEmpty()) {
            error.postValue("Token not found. Please login first.")
        }
    }

    private fun initWebSocket() {
        wsManager = WebSocketManager(
            conversationId = conversationId,
            token = token,
            onMessageReceived = { msg -> appendMessage(msg) },
            onConnected = {
                loadMessages()
                flushPendingMessages() // Item 19: enviar pendentes ao reconectar
            },
            onDisconnected = { wsStatus.postValue(WsStatus.DISCONNECTED) },
            onStatusChanged = { status -> wsStatus.postValue(status) },
            onTypingReceived = { username, isTyping ->
                typingUser.postValue(if (isTyping) username else null)
            }
        )
        wsManager.connect()
    }

    fun loadMessages() {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                error.postValue(null)

                val bearerToken = "Bearer $token"
                val result = RetrofitClient.api.getMessages(bearerToken, conversationId)

                messageList.clear()
                messageList.addAll(result.sortedBy { it.created_at })
                messages.postValue(messageList.toList())

                // Item 20: Mark as read automático
                markOtherMessagesAsRead()
            } catch (e: Exception) {
                error.postValue("Erro ao carregar: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) {
            error.postValue("Mensagem não pode estar vazia")
            return
        }

        if (wsStatus.value == WsStatus.CONNECTED) {
            wsManager.sendTyping(false)
            wsManager.sendMessage(text)
        } else {
            sendMessageViaRestOrQueue(text)
        }
    }

    fun sendTyping(isTyping: Boolean) {
        if (wsStatus.value == WsStatus.CONNECTED) {
            wsManager.sendTyping(isTyping)
        }
    }

    private fun sendMessageViaRestOrQueue(text: String) {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                val bearerToken = "Bearer $token"
                RetrofitClient.api.sendMessage(
                    bearerToken,
                    mapOf(
                        "conversation_id" to conversationId,
                        "content" to text
                    )
                )
                loadMessages()
            } catch (e: Exception) {
                // Item 19: Enfileirar para envio posterior
                pendingMessages.add(text)
                error.postValue("Sem conexão. Mensagem será enviada quando reconectar.")

                // Adicionar visualmente como pendente
                val pendingMsg = Message(
                    id = -(pendingMessages.size),
                    sender = currentUsername,
                    content = text,
                    created_at = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date()),
                    conversation = conversationId
                )
                messageList.add(pendingMsg)
                messages.postValue(messageList.toList())
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    // Item 19: Enviar mensagens pendentes ao reconectar
    private fun flushPendingMessages() {
        if (pendingMessages.isEmpty()) return

        viewModelScope.launch {
            val toSend = pendingMessages.toList()
            pendingMessages.clear()

            for (text in toSend) {
                try {
                    val bearerToken = "Bearer $token"
                    RetrofitClient.api.sendMessage(
                        bearerToken,
                        mapOf(
                            "conversation_id" to conversationId,
                            "content" to text
                        )
                    )
                } catch (_: Exception) {
                    // Re-enfileirar se falhar de novo
                    pendingMessages.add(text)
                }
            }
            // Recarregar para sincronizar IDs reais
            loadMessages()
        }
    }

    // Item 20: Marcar mensagens do outro pai como lidas
    private fun markOtherMessagesAsRead() {
        viewModelScope.launch {
            val bearerToken = "Bearer $token"
            for (msg in messageList) {
                if (msg.sender != currentUsername && msg.id > 0) {
                    // Verificar se já foi lida (sem read_by do user atual)
                    val alreadyRead = msg.read_by?.any { it.reader_name == currentUsername } == true
                    if (!alreadyRead) {
                        try {
                            RetrofitClient.api.markMessageAsRead(bearerToken, msg.id)
                        } catch (_: Exception) {
                            // Silent fail — não bloquear por falha de mark read
                        }
                    }
                }
            }
        }
    }

    fun markAsRead(messageId: Int) {
        viewModelScope.launch {
            try {
                val bearerToken = "Bearer $token"
                RetrofitClient.api.markMessageAsRead(bearerToken, messageId)
            } catch (_: Exception) {}
        }
    }

    // Item 27: Enviar mensagem com anexo
    fun sendMessageWithAttachment(text: String, uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                val bearerToken = "Bearer $token"

                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                // Get filename
                var fileName = "attachment"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val bytes = inputStream.readBytes()
                inputStream.close()

                val requestFile = okhttp3.RequestBody.create(
                    mimeType.toMediaType(), bytes
                )
                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "attachment", fileName, requestFile
                )

                val convIdBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(), conversationId.toString()
                )
                val contentBody = okhttp3.RequestBody.create(
                    "text/plain".toMediaType(), text
                )

                RetrofitClient.api.sendMessageWithAttachment(
                    bearerToken, convIdBody, contentBody, filePart
                )
                loadMessages()
            } catch (e: Exception) {
                error.postValue("Erro ao enviar anexo: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    private fun appendMessage(msg: IncomingMessage) {
        val newMsg = Message(
            id = msg.messageId,
            sender = msg.senderUsername,
            content = msg.content,
            created_at = msg.createdAt,
            conversation = conversationId
        )
        messageList.add(newMsg)
        messages.postValue(messageList.toList())

        // Item 20: marcar como lida se é mensagem do outro pai
        if (msg.senderUsername != currentUsername) {
            markAsRead(msg.messageId)
        }
    }

    override fun onCleared() {
        wsManager.disconnect()
        super.onCleared()
    }
}

class ChatViewModelFactory(
    private val context: Context,
    private val conversationId: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(context, conversationId) as T
    }
}
