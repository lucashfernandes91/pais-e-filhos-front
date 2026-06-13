package com.example.chatapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.ChatViewModel
import com.example.chatapp.ChatViewModelFactory
import com.example.chatapp.RetrofitClient
import com.example.chatapp.Message
import com.example.chatapp.MessageAdapter
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.WsStatus
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class ChatFragment : Fragment() {
    private lateinit var viewModel: ChatViewModel
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var attachButton: ImageButton
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var tvChatName: TextView
    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvChatStatus: TextView
    private lateinit var emptyState: View
    private lateinit var errorState: View
    private var hasOtherParent = false
    private var currentUsername: String = ""

    // Search
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

    // Attachment
    private var pendingAttachmentUri: Uri? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingAttachmentUri = uri
            val fileName = getFileName(uri)
            Toast.makeText(requireContext(), "Anexo: $fileName", Toast.LENGTH_SHORT).show()
            messageInput.hint = "📎 $fileName"
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
        observeViewModel()
        setupRecyclerView()
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

        // Dados dinâmicos do outro pai
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
        view?.findViewById<TextView>(R.id.tvEmptyChatTitle)?.setText(R.string.ui_nenhuma_mensagem_ainda)
        view?.findViewById<TextView>(R.id.tvEmptyChatMessage)
            ?.setText(R.string.ui_comece_uma_conversa_todas_as_mensagens_ficam_registradas)
        renderConnectionStatus(viewModel.wsStatus.value)
    }

    private fun renderNoCoparent() {
        hasOtherParent = false
        tvChatName.setText(R.string.chat_no_coparent_title)
        tvAvatarInitial.setText(R.string.chat_avatar_fallback)
        tvChatStatus.setText(R.string.chat_invite_coparent_status)
        tvChatStatus.setTextColor(resources.getColor(R.color.gray_400, null))
        pendingAttachmentUri = null
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
        setSendEnabled(available)
    }

    private fun setSendEnabled(enabled: Boolean) {
        sendButton.isEnabled = enabled
        sendButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun renderConnectionStatus(status: WsStatus?) {
        if (!hasOtherParent) {
            tvChatStatus.setText(R.string.chat_invite_coparent_status)
            tvChatStatus.setTextColor(resources.getColor(R.color.gray_400, null))
            return
        }
        when (status) {
            WsStatus.CONNECTED -> {
                tvChatStatus.setText(R.string.chat_status_connected)
                tvChatStatus.setTextColor(resources.getColor(R.color.success, null))
            }
            WsStatus.RECONNECTING -> {
                tvChatStatus.setText(R.string.chat_status_reconnecting)
                tvChatStatus.setTextColor(resources.getColor(R.color.warning, null))
            }
            WsStatus.ERROR -> {
                tvChatStatus.setText(R.string.chat_status_error)
                tvChatStatus.setTextColor(resources.getColor(R.color.error, null))
            }
            else -> {
                tvChatStatus.setText(R.string.chat_status_disconnected)
                tvChatStatus.setTextColor(resources.getColor(R.color.gray_400, null))
            }
        }
    }

    private fun initViewModel() {
        val conversationId = PrefsHelper.getConversationId(requireContext())
        val factory = ChatViewModelFactory(requireContext(), conversationId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(emptyList(), currentUsername)
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true  // Coloca items do fim para o topo (scroll automático ao fim)
                reverseLayout = false
            }
            adapter = messageAdapter
            setHasFixedSize(false)  // Permite ajuste de tamanho quando novas mensagens chegam
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (pendingAttachmentUri != null) {
                viewModel.sendMessageWithAttachment(text, pendingAttachmentUri!!, requireContext())
                messageInput.text?.clear()
                messageInput.hint = "Mensagem"
                pendingAttachmentUri = null
            } else if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                messageInput.text?.clear()
            }
        }

        attachButton.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "arquivo"
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    // ── Search ──────────────────────────────────────────────

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
            } else false
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

        // Hide keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

        // Reset highlights
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
            currentMatchIndex = searchMatchPositions.size - 1 // start from last (most recent)
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
        val position = searchMatchPositions[currentMatchIndex]
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
            "${currentMatchIndex + 1} de ${searchMatchPositions.size}"
        } else if (hasQuery) {
            "0 resultados"
        } else ""
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
        val sheet = ExportBottomSheet.newInstance(allMessages.size)
        sheet.show(childFragmentManager, "ExportBottomSheet")
    }

    // ── Observers ───────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            allMessages = messages
            messageAdapter = MessageAdapter(messages, currentUsername)
            messagesRecyclerView.adapter = messageAdapter

            // Marcar mensagens não lidas como lidas
            markUnreadMessagesAsRead(messages)

            // Empty state
            errorState.visibility = View.GONE
            if (messages.isEmpty()) {
                messagesRecyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                messagesRecyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                // Scroll para a última mensagem de forma garantida
                messagesRecyclerView.post {
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                        android.util.Log.d("ChatFragment", "scrollToPosition: ${messages.size - 1}")
                    }
                }
            }

            // Re-apply search if active
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
            setSendEnabled(hasOtherParent && !isLoading)
        }

        // Item 6: Typing indicator
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

        // Send typing on text change
        messageInput.addTextChangedListener(object : TextWatcher {
            private var typingTimer: java.util.Timer? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.sendTyping(true)
                typingTimer?.cancel()
                typingTimer = java.util.Timer()
                typingTimer?.schedule(object : java.util.TimerTask() {
                    override fun run() {
                        viewModel.sendTyping(false)
                    }
                }, 2000)
            }
        })
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun markUnreadMessagesAsRead(messages: List<Message>) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        val unread = messages.filter { msg ->
            msg.sender != currentUsername &&
            msg.read_by?.any { it.reader_name == currentUsername } != true
        }

        if (unread.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            unread.forEach { msg ->
                try {
                    RetrofitClient.api.markMessageAsRead("Bearer $token", msg.id)
                } catch (_: Exception) {}
            }
        }
    }
}
