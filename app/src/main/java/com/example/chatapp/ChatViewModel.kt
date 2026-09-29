package com.example.chatapp

import android.content.Context
import androidx.lifecycle.*
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.util.UUID

class ChatViewModel(
    private val context: Context,
    private val conversationId: Int
) : ViewModel() {

    companion object {
        // Deve acompanhar o page size padrão do backend (list_messages).
        const val PAGE_SIZE = 100
    }

    val messages = MutableLiveData<List<Message>>()
    val isLoading = MutableLiveData<Boolean>()
    val error = MutableLiveData<String?>()
    val loadError = MutableLiveData<Boolean>(false)
    val wsStatus = MutableLiveData<WsStatus>()
    val typingUser = MutableLiveData<String?>(null)
    val attachmentUploadProgress = MutableLiveData<Int?>(null)
    val attachmentUploadSucceeded = MutableLiveData<Boolean>()
    val attachmentUploadInProgress = MutableLiveData(false)
    private var attachmentUploadRunning = false

    fun consumeAttachmentUploadSuccess() {
        attachmentUploadSucceeded.value = false
    }

    private val messageList = mutableListOf<Message>()
    private var token: String = ""
    private var wsManager: WebSocketManager? = null
    private var markingMessagesAsRead = false
    private var flushingPendingMessages = false
    private val messageRepository = MessageRepository(context)
    val pagedMessages: Flow<PagingData<MessageEntity>> =
        if (conversationId > PrefsHelper.NO_CONVERSATION_ID) {
            messageRepository.pagingMessages(conversationId)
        } else {
            flowOf(PagingData.empty())
        }
    private val currentUsername by lazy { PrefsHelper.getUsername(context) }

    private val pendingMessages = PrefsHelper.getPendingMessages(context)
        .filter { it.conversationId == conversationId }
        .toMutableList()

    init {
        loadToken()
        if (conversationId > PrefsHelper.NO_CONVERSATION_ID) {
            restorePendingMessages()
            observeCachedMessages()
            initWebSocket()
        } else {
            messages.value = emptyList()
            wsStatus.value = WsStatus.DISCONNECTED
        }

    }

    private fun observeCachedMessages() {
        viewModelScope.launch {
            try {
                messageRepository.observeConversation(conversationId).collect { cached ->
                if (cached.isEmpty() || isLoading.value == true) return@collect
                messageList.clear()
                messageList.addAll(cached.map(MessageEntity::toMessage))
                restorePendingMessages()
                messages.postValue(messageList.toList())
                }
            } catch (error: Exception) {
                AppTelemetry.error(
                    "room_message_observation_failed",
                    error = error,
                    attributes = mapOf("operation" to "observe_conversation")
                )
            }
        }
    }

    private fun loadToken() {
        token = PrefsHelper.getAuthToken(context)
        if (token.isEmpty()) {
            error.postValue("Token not found. Please login first.")
        }
    }

    private fun currentBearerToken(): String? {
        token = PrefsHelper.getAuthToken(context)
        if (token.isEmpty()) {
            error.postValue("Token not found. Please login first.")
            return null
        }
        return "Bearer $token"
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
        wsManager?.connect()
    }

    fun loadMessages() {
        if (conversationId <= PrefsHelper.NO_CONVERSATION_ID) {
            messageList.clear()
            messages.value = emptyList()
            isLoading.value = false
            loadError.value = false
            return
        }
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                error.postValue(null)
                loadError.postValue(false)

                val bearerToken = currentBearerToken() ?: return@launch
                val result = messageRepository.refresh(conversationId, bearerToken)

                messageList.clear()
                messageList.addAll(result.sortedBy { it.created_at })
                restorePendingMessages()
                messages.postValue(messageList.toList())

                // Item 20: Mark as read automático
                markOtherMessagesAsRead()
            } catch (e: Exception) {
                loadError.postValue(true)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun sendMessage(text: String) {
        if (conversationId <= PrefsHelper.NO_CONVERSATION_ID) {
            error.postValue("Convide o outro responsável para iniciar a conversa.")
            return
        }
        if (text.isBlank()) {
            error.postValue("Mensagem não pode estar vazia")
            return
        }
        if (text.length > AttachmentPolicy.MAX_MESSAGE_LENGTH) {
            error.postValue("A mensagem deve ter no máximo ${AttachmentPolicy.MAX_MESSAGE_LENGTH} caracteres.")
            return
        }

        val clientMessageId = UUID.randomUUID().toString()
        val pending = PrefsHelper.PendingMessage(
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            content = text,
            createdAt = nowTimestamp()
        )
        enqueuePendingMessage(pending)
        addPendingMessageToList(pending)

        if (wsStatus.value == WsStatus.CONNECTED &&
            wsManager?.sendMessage(text, clientMessageId) == true
        ) {
            wsManager?.sendTyping(false)
        } else {
            sendMessageViaRestOrQueue(pending)
        }
    }

    fun sendTyping(isTyping: Boolean) {
        if (wsStatus.value == WsStatus.CONNECTED) {
            wsManager?.sendTyping(isTyping)
        }
    }

    private fun sendMessageViaRestOrQueue(message: PrefsHelper.PendingMessage) {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                val bearerToken = currentBearerToken() ?: return@launch
                RetrofitClient.api.sendMessage(
                    bearerToken,
                    mapOf(
                        "conversation_id" to conversationId,
                        "content" to message.content,
                        "client_message_id" to message.clientMessageId
                    )
                )
                pendingMessages.removeAll { it.clientMessageId == message.clientMessageId }
                persistPendingMessages()
                loadMessages()
            } catch (e: Exception) {
                enqueuePendingMessage(message)
                AppTelemetry.warning(
                    "offline_message_queued",
                    attributes = mapOf("operation" to "send_message")
                )
                error.postValue("Sem conexão. Mensagem será enviada quando reconectar.")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    private fun flushPendingMessages() {
        if (pendingMessages.isEmpty() || flushingPendingMessages) return

        flushingPendingMessages = true
        viewModelScope.launch {
            try {
                for (message in pendingMessages.toList()) {
                    try {
                        val bearerToken = currentBearerToken() ?: return@launch
                        RetrofitClient.api.sendMessage(
                            bearerToken,
                            mapOf(
                                "conversation_id" to conversationId,
                                "content" to message.content,
                                "client_message_id" to message.clientMessageId
                            )
                        )
                        pendingMessages.removeAll { it.clientMessageId == message.clientMessageId }
                        persistPendingMessages()
                    } catch (_: Exception) {
                        AppTelemetry.warning("offline_queue_flush_stopped")
                        break
                    }
                }
                if (pendingMessages.isEmpty()) {
                    loadMessages()
                }
            } finally {
                flushingPendingMessages = false
            }
        }
    }

    private fun enqueuePendingMessage(message: PrefsHelper.PendingMessage) {
        if (pendingMessages.none { it.clientMessageId == message.clientMessageId }) {
            pendingMessages += message
            persistPendingMessages()
            AppTelemetry.info(
                "offline_queue_message_added",
                attributes = mapOf("queue_size" to pendingMessages.size.toString())
            )
        }
    }

    private fun addPendingMessageToList(message: PrefsHelper.PendingMessage) {
        if (messageList.any { it.client_message_id == message.clientMessageId }) return
        messageList.add(
            Message(
                id = -messageList.count { it.id < 0 } - 1,
                sender = currentUsername,
                content = message.content,
                created_at = message.createdAt,
                conversation = conversationId,
                client_message_id = message.clientMessageId
            )
        )
        messages.postValue(messageList.toList())
        viewModelScope.launch {
            messageRepository.cache(listOf(messageList.last()))
        }
    }

    private fun restorePendingMessages() {
        pendingMessages
            .sortedBy { it.createdAt }
            .forEach(::addPendingMessageToList)
    }

    private fun persistPendingMessages() {
        PrefsHelper.savePendingMessagesForConversation(context, conversationId, pendingMessages)
    }

    private fun nowTimestamp(): String =
        java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

    // Item 20: Marcar mensagens do outro pai como lidas
    private fun markOtherMessagesAsRead() {
        if (conversationId <= PrefsHelper.NO_CONVERSATION_ID || markingMessagesAsRead) return
        if (messageList.none { it.sender != currentUsername && it.id > 0 }) return

        markingMessagesAsRead = true
        viewModelScope.launch {
            try {
                val bearerToken = currentBearerToken() ?: return@launch
                RetrofitClient.api.markConversationMessagesAsRead(
                    bearerToken,
                    mapOf("conversation_id" to conversationId)
                )
            } catch (_: Exception) {
                // A leitura não deve bloquear a exibição da conversa.
            } finally {
                markingMessagesAsRead = false
            }
        }
    }

    // Item 27: Enviar mensagem com anexo
    fun sendMessageWithAttachment(text: String, uri: android.net.Uri, context: android.content.Context) {
        if (conversationId <= PrefsHelper.NO_CONVERSATION_ID) {
            error.postValue("Convide o outro responsável para iniciar a conversa.")
            return
        }
        if (attachmentUploadRunning) return
        attachmentUploadRunning = true
        attachmentUploadInProgress.postValue(true)
        viewModelScope.launch {
            var processedFile: File? = null
            try {
                isLoading.postValue(true)
                attachmentUploadProgress.postValue(0)
                attachmentUploadSucceeded.postValue(false)
                val bearerToken = currentBearerToken() ?: return@launch
                require(text.length <= AttachmentPolicy.MAX_MESSAGE_LENGTH) {
                    "message_too_long"
                }

                val attachmentInfo = AttachmentPolicy.inspect(context, uri).getOrThrow()
                var mimeType = attachmentInfo.mimeType
                var fileName = attachmentInfo.fileName

                val compressed = if (attachmentInfo.extension in setOf("jpg", "jpeg", "png", "webp")) {
                    ImageCompressor.compress(context, uri)
                } else {
                    null
                }

                if (attachmentInfo.extension in setOf("jpg", "jpeg", "png", "webp")) {
                    val bytes = compressed ?: throw IllegalArgumentException("image_processing_failed")
                    mimeType = "image/jpeg"
                    fileName = fileName.substringBeforeLast('.', fileName) + ".jpg"
                    if (bytes.size > AttachmentPolicy.MAX_FILE_SIZE_BYTES) {
                        throw IllegalArgumentException("file_too_large")
                    }
                    processedFile = File.createTempFile("chat-upload-", ".tmp", context.cacheDir)
                    processedFile.writeBytes(bytes)
                } else {
                    processedFile = copyUriToTemp(context, uri)
                }

                val requestFile = ProgressRequestBody(
                    processedFile!!,
                    mimeType.toMediaType()
                ) { uploaded, total ->
                    attachmentUploadProgress.postValue(
                        ((uploaded * 100L) / total.coerceAtLeast(1L)).toInt()
                    )
                }
                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "attachment", fileName, requestFile
                )

                val convIdBody = conversationId.toString()
                    .toRequestBody("text/plain".toMediaType())
                val contentBody = text.toRequestBody("text/plain".toMediaType())

                RetrofitClient.api.sendMessageWithAttachment(
                    bearerToken, convIdBody, contentBody, filePart
                )
                attachmentUploadSucceeded.postValue(true)
                loadMessages()
            } catch (e: Exception) {
                error.postValue(backendErrorMessage(e))
            } finally {
                processedFile?.delete()
                attachmentUploadProgress.postValue(null)
                isLoading.postValue(false)
                attachmentUploadInProgress.postValue(false)
                attachmentUploadRunning = false
            }

        }
    }

    private fun backendErrorMessage(error: Exception): String {
        val responseBody = (error as? retrofit2.HttpException)?.response()?.errorBody()?.string()
        if (!responseBody.isNullOrBlank()) {
            val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1)
            if (!message.isNullOrBlank()) return message
        }
        return "Erro ao enviar anexo: ${error.message ?: "falha desconhecida"}"
    }

    private fun copyUriToTemp(context: android.content.Context, uri: android.net.Uri): File {
        val file = File.createTempFile("chat-upload-", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            total += read
                            if (total > AttachmentPolicy.MAX_FILE_SIZE_BYTES) {
                                throw IllegalArgumentException("file_too_large")
                            }
                            output.write(buffer, 0, read)
                        }
                        read = input.read(buffer)
                    }
                    if (total == 0L) throw IllegalArgumentException("file_read_failed")
                }
            } ?: throw IllegalArgumentException("file_read_failed")
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun appendMessage(msg: IncomingMessage) {
        if (msg.messageId > 0 && messageList.any { it.id == msg.messageId }) return
        val pendingIndex = messageList.indexOfFirst {
            it.id < 0 && !msg.clientMessageId.isNullOrBlank() &&
                it.client_message_id == msg.clientMessageId
        }
        if (pendingIndex >= 0) {
            messageList[pendingIndex] = Message(
                id = msg.messageId,
                sender = msg.senderUsername,
                content = msg.content,
                created_at = msg.createdAt,
                conversation = conversationId,
                client_message_id = msg.clientMessageId
            )
            pendingMessages.removeAll { it.clientMessageId == msg.clientMessageId }
            persistPendingMessages()
            messages.postValue(messageList.toList())
            viewModelScope.launch {
                messageRepository.cache(listOf(messageList[pendingIndex]))
            }
            return
        }

        val newMsg = Message(
            id = msg.messageId,
            sender = msg.senderUsername,
            content = msg.content,
            created_at = msg.createdAt,
            conversation = conversationId,
            client_message_id = msg.clientMessageId
        )
        messageList.add(newMsg)
        messages.postValue(messageList.toList())
        viewModelScope.launch {
            messageRepository.cache(listOf(newMsg))
        }

        // Item 20: marcar como lida se é mensagem do outro pai
        if (msg.senderUsername != currentUsername) {
            markOtherMessagesAsRead()
        }

    }

    override fun onCleared() {
        wsManager?.disconnect()
        super.onCleared()
    }
}

private class ProgressRequestBody(
    private val file: File,
    private val mediaType: okhttp3.MediaType,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var uploaded = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) {
                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, total)
                }
                read = input.read(buffer)
            }
        }
    }
}

class ChatViewModelFactory(
    private val context: Context,
    private val conversationId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return modelClass.cast(ChatViewModel(context, conversationId))
            ?: throw IllegalStateException("Unable to create ${modelClass.name}")
    }
}
