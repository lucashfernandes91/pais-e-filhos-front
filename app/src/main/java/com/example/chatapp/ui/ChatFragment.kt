package com.example.chatapp.ui

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.ChatViewModel
import com.example.chatapp.ChatViewModelFactory
import com.example.chatapp.AppDateTime
import com.example.chatapp.AttachmentPolicy
import com.example.chatapp.ChatItem
import com.example.chatapp.ChatPagingItemMapper
import com.example.chatapp.ImageViewerActivity
import com.example.chatapp.Message
import com.example.chatapp.MessageAdapter
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.example.chatapp.WsStatus
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var attachmentPreview: View
    private lateinit var attachmentDetails: TextView
    private lateinit var attachmentCancelButton: ImageButton
    private lateinit var attachmentProgress: ProgressBar
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var tvChatName: TextView
    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvChatStatus: TextView
    private lateinit var emptyState: View
    private lateinit var errorState: View
    private var hasOtherParent = false
    private var currentUsername: String = ""

    private lateinit var headerNormal: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var tvSearchCount: TextView
    private lateinit var btnSearchUp: ImageButton
    private lateinit var btnSearchDown: ImageButton

    private var isSearchActive = false
    private var searchMatchPositions = mutableListOf<Int>()
    private var currentMatchIndex = -1
    private var allMessages = listOf<Message>()

    private var pendingAttachmentUri: Uri? = null
    private var pendingAttachmentInfo: com.example.chatapp.AttachmentInfo? = null
    private var typingStopJob: Job? = null
    private var isLocalTyping = false
    private var initialChatScrollDone = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = AttachmentPolicy.inspect(requireContext(), uri)
            result.onSuccess { info ->
                pendingAttachmentUri = uri
                pendingAttachmentInfo = info
                renderAttachmentPreview(info)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.chat_attachment_selected_toast, info.fileName),
                    Toast.LENGTH_SHORT
                ).show()
                messageInput.hint = getString(R.string.chat_attachment_selected_hint, info.fileName)
            }.onFailure { error ->
                val message = when (error.message) {
                    "file_too_large" -> R.string.chat_attachment_too_large
                    else -> R.string.chat_attachment_unsupported
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUsername = PrefsHelper.getUsername(requireContext())

        initViewModel()
        initViews(view)
        setupRecyclerView()
        observeViewModel()
        setupSendButton()
        setupSearch(view)
        loadActiveConversationParticipant()
        viewModel.loadMessages()
    }

    private fun initViews(view: View) {
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        attachButton = view.findViewById(R.id.btnAttach)
        attachmentPreview = view.findViewById(R.id.attachmentPreview)
        attachmentDetails = view.findViewById(R.id.attachmentDetails)
        attachmentCancelButton = view.findViewById(R.id.btnAttachmentCancel)
        attachmentProgress = view.findViewById(R.id.attachmentProgress)
        tvChatName = view.findViewById(R.id.tvChatName)
        tvAvatarInitial = view.findViewById(R.id.tvAvatarInitial)
        tvChatStatus = view.findViewById(R.id.tvChatStatus)
        emptyState = view.findViewById(R.id.emptyStateChat)
        errorState = view.findViewById(R.id.errorStateChat)
        headerNormal = view.findViewById(R.id.headerNormal)
        searchBar = view.findViewById(R.id.searchBar)
        searchInput = view.findViewById(R.id.searchInput)
        tvSearchCount = view.findViewById(R.id.tvSearchCount)
        btnSearchUp = view.findViewById(R.id.btnSearchUp)
        btnSearchDown = view.findViewById(R.id.btnSearchDown)

        val cachedName = PrefsHelper.getOtherParentName(requireContext())
        if (cachedName.isBlank()) renderNoCoparent() else renderChatParticipant(cachedName)

        view.findViewById<View>(R.id.btnRetryChat).setOnClickListener {
            errorState.visibility = View.GONE
            viewModel.loadMessages()
        }
    }

    private fun loadActiveConversationParticipant() {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        val activeConversationId = PrefsHelper.getConversationId(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val conversation = RetrofitClient.api.getConversations("Bearer $token")
                    .firstOrNull { it.id == activeConversationId }
                    ?: return@launch
                val otherParent = conversation.participants.firstOrNull { !it.is_me }
                if (otherParent == null) {
                    PrefsHelper.saveOtherParentName(requireContext(), "")
                    renderNoCoparent()
                } else {
                    PrefsHelper.saveOtherParentName(requireContext(), otherParent.username)
                    renderChatParticipant(otherParent.username)
                }
            } catch (_: Exception) {
                // Keep cached data or placeholder when header refresh is unavailable.
            }
        }
    }

    private fun renderChatParticipant(name: String) {
        hasOtherParent = true
        val displayName = name.trim().replaceFirstChar { it.uppercase() }
        tvChatName.text = displayName
        tvAvatarInitial.text = displayName.firstOrNull()?.uppercase()
            ?: getString(R.string.chat_avatar_fallback)
        setComposerAvailable(true)
        view?.findViewById<TextView>(R.id.tvEmptyChatTitle)
            ?.setText(R.string.ui_nenhuma_mensagem_ainda)
        view?.findViewById<TextView>(R.id.tvEmptyChatMessage)
            ?.setText(R.string.ui_comece_uma_conversa_todas_as_mensagens_ficam_registradas)
        renderConnectionStatus(viewModel.wsStatus.value)
    }

    private fun renderNoCoparent() {
        hasOtherParent = false
        tvChatName.setText(R.string.chat_no_coparent_title)
        tvAvatarInitial.setText(R.string.chat_avatar_fallback)
        tvChatStatus.setText(R.string.chat_invite_coparent_status)
        tvChatStatus.setTextColor(requireColor(R.color.gray_500))
        pendingAttachmentUri = null
        pendingAttachmentInfo = null
        attachmentPreview.visibility = View.GONE
        messageInput.text?.clear()
        setComposerAvailable(false)
        view?.findViewById<TextView>(R.id.tvEmptyChatTitle)?.setText(R.string.chat_no_coparent_title)
        view?.findViewById<TextView>(R.id.tvEmptyChatMessage)?.setText(R.string.chat_invite_to_start)
    }

    private fun setComposerAvailable(available: Boolean) {
        messageInput.isEnabled = available
        messageInput.alpha = if (available) 1f else 0.62f
        messageInput.hint = getString(
            if (available) R.string.ui_mensagem else R.string.chat_composer_unavailable_hint
        )
        attachButton.isEnabled = available
        attachButton.alpha = if (available) 1f else 0.42f
        if (!available) {
            stopLocalTyping()
        }
        setSendEnabled(available)
    }

    private fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun renderConnectionStatus(status: WsStatus?) {
        if (!hasOtherParent) {
            tvChatStatus.setText(R.string.chat_invite_coparent_status)
            tvChatStatus.setTextColor(requireColor(R.color.gray_500))
            return
        }
        when (status) {
            WsStatus.CONNECTED -> {
                tvChatStatus.setText(R.string.chat_status_connected)
                tvChatStatus.setTextColor(requireColor(R.color.feedback_success_text))
            }
            WsStatus.RECONNECTING -> {
                tvChatStatus.setText(R.string.chat_status_reconnecting)
                tvChatStatus.setTextColor(requireColor(R.color.warning_text))
            }
            WsStatus.ERROR -> {
                tvChatStatus.setText(R.string.chat_status_error)
                tvChatStatus.setTextColor(requireColor(R.color.feedback_error_text))
            }
            else -> {
                tvChatStatus.setText(R.string.chat_status_disconnected)
                tvChatStatus.setTextColor(requireColor(R.color.gray_500))
            }
        }
    }

    private fun initViewModel() {
        val conversationId = PrefsHelper.getConversationId(requireContext())
        val factory = ChatViewModelFactory(requireContext(), conversationId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            currentUsername,
            onImageClick = { imageUrl ->
                ImageViewerActivity.start(requireContext(), imageUrl)
            },
            onDocumentClick = ::downloadDocument
        )
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
                reverseLayout = false
            }
            adapter = messageAdapter
            setHasFixedSize(false)
        }
        messageAdapter.addLoadStateListener { state ->
            if (
                !initialChatScrollDone &&
                state.refresh is LoadState.NotLoading &&
                messageAdapter.itemCount > 0
            ) {
                initialChatScrollDone = true
                messagesRecyclerView.post {
                    messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                }
            }
        }

    }

    private fun downloadDocument(url: String, attachmentName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val fileName = attachmentName.substringAfterLast('/').substringBefore('?')
                    .takeIf { it.isNotBlank() }
                    ?.let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
                    ?: "documento.pdf"

                val savedUri = withContext(Dispatchers.IO) {
                    val token = PrefsHelper.getAuthToken(requireContext())
                    val responseBody = RetrofitClient.api.downloadAttachment("Bearer $token", url)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val resolver = requireContext().contentResolver
                        val uri = resolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: error("download_destination_unavailable")
                        try {
                            resolver.openOutputStream(uri)?.use { output ->
                                responseBody.byteStream().use { input -> input.copyTo(output) }
                            } ?: error("download_destination_unavailable")
                            uri
                        } catch (error: Exception) {
                            resolver.delete(uri, null, null)
                            throw error
                        } finally {
                            responseBody.close()
                        }
                    } else {
                        val directory = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                        if (!directory.exists() && !directory.mkdirs()) {
                            error("download_destination_unavailable")
                        }
                        FileOutputStream(File(directory, fileName)).use { output ->
                            responseBody.byteStream().use { input -> input.copyTo(output) }
                        }
                        responseBody.close()
                        androidx.core.content.FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            File(directory, fileName)
                        )
                    }
                }
                Toast.makeText(requireContext(), R.string.chat_attachment_downloaded, Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(savedUri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.chat_attachment_download_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            if (viewModel.attachmentUploadInProgress.value == true) return@setOnClickListener
            val text = messageInput.text.toString().trim()
            if (text.length > AttachmentPolicy.MAX_MESSAGE_LENGTH) {
                Toast.makeText(requireContext(), R.string.chat_message_too_long, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pendingAttachmentUri != null) {
                viewModel.sendMessageWithAttachment(text, pendingAttachmentUri!!, requireContext())
                stopLocalTyping()
            } else if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                messageInput.text?.clear()
                stopLocalTyping()
            }
        }

        attachButton.setOnClickListener {
            filePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
        }
        attachmentCancelButton.setOnClickListener { clearPendingAttachment() }
    }

    private fun renderAttachmentPreview(info: com.example.chatapp.AttachmentInfo) {
        attachmentDetails.text = getString(
            R.string.chat_attachment_details,
            info.fileName,
            info.mimeType,
            formatAttachmentSize(info.sizeBytes)
        )
        attachmentPreview.visibility = View.VISIBLE
    }

    private fun clearPendingAttachment() {
        pendingAttachmentUri = null
        pendingAttachmentInfo = null
        attachmentPreview.visibility = View.GONE
        resetComposerHint()
    }

    private fun formatAttachmentSize(sizeBytes: Long): String {
        if (sizeBytes < 0) return getString(R.string.chat_attachment_size_unknown)
        return if (sizeBytes < 1024 * 1024) {
            getString(R.string.chat_attachment_size_kb, (sizeBytes / 1024.0).toInt().coerceAtLeast(1))
        } else {
            getString(R.string.chat_attachment_size_mb, sizeBytes / (1024.0 * 1024.0))
        }
    }

    private fun resetComposerHint() {
        messageInput.hint = getString(R.string.ui_mensagem)
    }

    private fun requireColor(colorRes: Int): Int {
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun setupSearch(view: View) {
        val btnSearch = view.findViewById<ImageButton>(R.id.btnSearch)
        val btnSearchClose = view.findViewById<ImageButton>(R.id.btnSearchClose)
        val btnExport = view.findViewById<ImageButton>(R.id.btnExport)

        btnSearch.setOnClickListener { openSearch() }
        btnSearchClose.setOnClickListener { closeSearch() }
        btnExport.setOnClickListener { openExportSheet() }

        btnSearchUp.setOnClickListener { navigateMatch(-1) }
        btnSearchDown.setOnClickListener { navigateMatch(1) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString().orEmpty())
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                navigateMatch(1)
                true
            } else {
                false
            }
        }
    }

    private fun openSearch() {
        isSearchActive = true
        headerNormal.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        isSearchActive = false
        searchBar.visibility = View.GONE
        headerNormal.visibility = View.VISIBLE
        searchInput.text?.clear()

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

        clearSearchHighlights()
    }

    private fun performSearch(query: String) {
        searchMatchPositions.clear()
        currentMatchIndex = -1

        if (query.isBlank()) {
            updateSearchUI()
            clearSearchHighlights()
            return
        }

        val lowerQuery = query.lowercase(Locale.getDefault())
        allMessages.forEachIndexed { index, message ->
            if (message.content.lowercase(Locale.getDefault()).contains(lowerQuery)) {
                searchMatchPositions.add(index)
            }
        }

        if (searchMatchPositions.isNotEmpty()) {
            currentMatchIndex = searchMatchPositions.size - 1
            scrollToCurrentMatch()
        }

        updateSearchUI()
        updateAdapterHighlights(query)
    }

    private fun navigateMatch(direction: Int) {
        if (searchMatchPositions.isEmpty()) return

        currentMatchIndex += direction
        when {
            currentMatchIndex >= searchMatchPositions.size -> currentMatchIndex = 0
            currentMatchIndex < 0 -> currentMatchIndex = searchMatchPositions.size - 1
        }

        scrollToCurrentMatch()
        updateSearchUI()
        updateAdapterHighlights(searchInput.text?.toString().orEmpty())
    }

    private fun scrollToCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= searchMatchPositions.size) return
        val position = messageAdapter.getAdapterPositionForMessage(
            searchMatchPositions[currentMatchIndex]
        )
        if (position == -1) return
        (messagesRecyclerView.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, messagesRecyclerView.height / 3)
    }

    private fun updateSearchUI() {
        val hasResults = searchMatchPositions.isNotEmpty()
        val hasQuery = searchInput.text?.isNotBlank() == true

        tvSearchCount.visibility = if (hasQuery) View.VISIBLE else View.GONE
        btnSearchUp.visibility = if (hasResults) View.VISIBLE else View.GONE
        btnSearchDown.visibility = if (hasResults) View.VISIBLE else View.GONE

        tvSearchCount.text = if (hasResults) {
            getString(
                R.string.chat_search_result_count,
                currentMatchIndex + 1,
                searchMatchPositions.size
            )
        } else if (hasQuery) {
            getString(R.string.chat_search_no_results)
        } else {
            ""
        }
    }

    private fun updateAdapterHighlights(query: String) {
        messageAdapter.updateSearch(query, currentMatchIndex, searchMatchPositions)
    }

    private fun clearSearchHighlights() {
        searchMatchPositions.clear()
        currentMatchIndex = -1
        messageAdapter.updateSearch("", -1, emptyList())
    }

    private fun openExportSheet() {
        ExportBottomSheet.show(this, allMessages.size, ExportBottomSheet.TYPE_MESSAGES)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pagedMessages.collectLatest { pagingData ->
                    val messageItems = pagingData.map { entity ->
                        val message = entity.toMessage()
                        ChatItem.MessageItem(message, allMessages.indexOfFirst {
                            messageKey(it) == messageKey(message)
                        })
                    }
                    val withDates = messageItems.insertSeparators { before, after ->
                        ChatPagingItemMapper.dateSeparator(before, after)
                    }
                    messageAdapter.submitData(
                        withDates.insertSeparators { before, after ->
                            ChatPagingItemMapper.unreadSeparator(currentUsername, before, after)
                        }
                    )
                }
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            allMessages = messages
            messageAdapter.refresh()

            errorState.visibility = View.GONE
            if (messages.isEmpty()) {
                messagesRecyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                messagesRecyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }

            if (isSearchActive && searchInput.text?.isNotBlank() == true) {
                performSearch(searchInput.text.toString())
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                showSnackbar(errorMsg)
            }
        }

        viewModel.loadError.observe(viewLifecycleOwner) { hasError ->
            if (hasError == true) {
                messagesRecyclerView.visibility = View.GONE
                emptyState.visibility = View.GONE
                errorState.visibility = View.VISIBLE
            }
        }

        viewModel.wsStatus.observe(viewLifecycleOwner) { status ->
            renderConnectionStatus(status)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val uploading = viewModel.attachmentUploadInProgress.value == true
            setSendEnabled(hasOtherParent && !isLoading && !uploading)
            attachButton.isEnabled = hasOtherParent && !isLoading && !uploading
            attachmentCancelButton.isEnabled = !isLoading && !uploading
        }
        viewModel.attachmentUploadInProgress.observe(viewLifecycleOwner) { uploading ->
            val loading = viewModel.isLoading.value == true
            setSendEnabled(hasOtherParent && !loading && !uploading)
            attachButton.isEnabled = hasOtherParent && !loading && !uploading
            attachmentCancelButton.isEnabled = !loading && !uploading
        }
        viewModel.attachmentUploadProgress.observe(viewLifecycleOwner) { progress ->
            attachmentProgress.visibility = if (progress == null) View.GONE else View.VISIBLE
            if (progress != null) attachmentProgress.progress = progress
        }
        viewModel.attachmentUploadSucceeded.observe(viewLifecycleOwner) { succeeded ->
            if (succeeded == true) {
                messageInput.text?.clear()
                clearPendingAttachment()
                stopLocalTyping()
                viewModel.consumeAttachmentUploadSuccess()
            }
        }

        viewModel.typingUser.observe(viewLifecycleOwner) { username ->
            val tvTyping = view?.findViewById<TextView>(R.id.tvTypingIndicator)
            if (username != null) {
                val displayName = username.replaceFirstChar { it.uppercase() }
                tvTyping?.text = getString(R.string.chat_typing_user, displayName)
                tvTyping?.visibility = View.VISIBLE
            } else {
                tvTyping?.visibility = View.GONE
            }
        }

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!hasOtherParent) return

                val hasDraft = !s.isNullOrBlank()
                if (!hasDraft) {
                    stopLocalTyping()
                    return
                }

                if (!isLocalTyping) {
                    viewModel.sendTyping(true)
                    isLocalTyping = true
                }

                typingStopJob?.cancel()
                typingStopJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(2000)
                    stopLocalTyping()
                }
            }
        })
    }

    private fun createDateSeparator(
        before: ChatItem?,
        after: ChatItem?
    ): ChatItem? {
        val afterMessage = (after as? ChatItem.MessageItem)?.message ?: return null
        val beforeMessage = (before as? ChatItem.MessageItem)?.message
        if (before == null || messageDateKey(beforeMessage) != messageDateKey(afterMessage)) {
            return ChatItem.DateDivider(formatDateLabel(afterMessage))
        }
        return null
    }

    private fun createUnreadSeparator(
        before: ChatItem?,
        after: ChatItem?
    ): ChatItem? {
        val afterMessage = (after as? ChatItem.MessageItem)?.message ?: return null
        val afterUnread = afterMessage.sender != currentUsername &&
            afterMessage.id > 0 &&
            afterMessage.read_by?.any { it.reader_name == currentUsername } != true
        val beforeMessage = (before as? ChatItem.MessageItem)?.message
        val beforeUnread = beforeMessage?.let {
            it.sender != currentUsername &&
                it.id > 0 &&
                it.read_by?.any { read -> read.reader_name == currentUsername } != true
        } == true
        return if (afterUnread && !beforeUnread) ChatItem.UnreadDivider else null
    }

    private fun messageDateKey(message: Message?): String =
        message?.let {
            AppDateTime.parseApi(it.created_at)?.let { date ->
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            }.orEmpty()
        }.orEmpty()

    private fun formatDateLabel(message: Message): String {
        val key = messageDateKey(message)
        if (key.isEmpty()) return message.created_at
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(key)
                ?: return key
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -1)
            }.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.time) }
            when (key) {
                today -> "Hoje"
                yesterday -> "Ontem"
                else -> SimpleDateFormat("dd 'de' MMMM", Locale.forLanguageTag("pt-BR"))
                    .format(parsed).replaceFirstChar { it.uppercase() }
            }
        } catch (_: Exception) {
            key
        }
    }

    private fun messageKey(message: Message): String =
        message.client_message_id?.takeIf { it.isNotBlank() }
            ?: if (message.id > 0) "server:${message.id}" else {
                "local:${message.created_at}:${message.content}"
            }

    override fun onDestroyView() {
        stopLocalTyping()
        super.onDestroyView()
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    /*
        return

        viewLifecycleOwner.lifecycleScope.launch {
            unread.forEach { msg ->
                try {
                    RetrofitClient.api.markMessageAsRead("Bearer $token", msg.id)
                } catch (_: Exception) {
                }
            }
        }
    }

        */

    private fun stopLocalTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        if (isLocalTyping) {
            viewModel.sendTyping(false)
            isLocalTyping = false
        }
    }
}
