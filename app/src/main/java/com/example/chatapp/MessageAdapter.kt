package com.example.chatapp

import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class MessageAdapter(
    messages: List<Message>,
    private val currentUsername: String = "current_user",
    private var searchQuery: String = "",
    private var activeMatchPosition: Int = -1,
    private var matchPositions: List<Int> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_DATE_DIVIDER = 2
        private const val VIEW_TYPE_UNREAD_DIVIDER = 3
        private const val HIGHLIGHT_COLOR = 0xFFFDE68A.toInt()
        private const val ACTIVE_HIGHLIGHT_COLOR = 0xFFFBBF24.toInt()
    }

    private val chatItems: List<ChatItem> = buildChatItems(messages)

    private fun buildChatItems(messages: List<Message>): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd 'de' MMMM", Locale("pt", "BR"))
        var lastDateStr = ""
        var unreadInserted = false

        for ((index, msg) in messages.withIndex()) {
            // Date divider
            val msgDateStr = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(msg.created_at)
                if (parsed != null) dateFormat.format(parsed) else ""
            } catch (_: Exception) { "" }

            if (msgDateStr.isNotEmpty() && msgDateStr != lastDateStr) {
                val label = try {
                    val parsed = dateFormat.parse(msgDateStr)
                    if (parsed != null) {
                        val today = dateFormat.format(Date())
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_MONTH, -1)
                        val yesterday = dateFormat.format(cal.time)
                        when (msgDateStr) {
                            today -> "Hoje"
                            yesterday -> "Ontem"
                            else -> displayFormat.format(parsed).replaceFirstChar { it.uppercase() }
                        }
                    } else msgDateStr
                } catch (_: Exception) { msgDateStr }
                items.add(ChatItem.DateDivider(label))
                lastDateStr = msgDateStr
            }

            // Unread divider (before first unread message from other)
            if (!unreadInserted && msg.sender != currentUsername && msg.id > 0) {
                val isUnread = msg.read_by?.any { it.reader_name == currentUsername } != true
                if (isUnread) {
                    items.add(ChatItem.UnreadDivider)
                    unreadInserted = true
                }
            }

            items.add(ChatItem.MessageItem(msg, index))
        }
        return items
    }

    fun updateSearch(query: String, activePos: Int, matches: List<Int>) {
        searchQuery = query
        activeMatchPosition = activePos
        matchPositions = matches
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = chatItems[position]) {
            is ChatItem.DateDivider -> VIEW_TYPE_DATE_DIVIDER
            is ChatItem.UnreadDivider -> VIEW_TYPE_UNREAD_DIVIDER
            is ChatItem.MessageItem -> {
                if (item.message.sender == currentUsername) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
            VIEW_TYPE_DATE_DIVIDER -> DateDividerViewHolder(inflater.inflate(R.layout.item_date_divider, parent, false))
            VIEW_TYPE_UNREAD_DIVIDER -> UnreadDividerViewHolder(inflater.inflate(R.layout.item_unread_divider, parent, false))
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = chatItems[position]) {
            is ChatItem.MessageItem -> {
                when (holder) {
                    is SentViewHolder -> holder.bind(item.message, item.originalIndex)
                    is ReceivedViewHolder -> holder.bind(item.message, item.originalIndex)
                }
            }
            is ChatItem.DateDivider -> (holder as DateDividerViewHolder).bind(item.dateLabel)
            is ChatItem.UnreadDivider -> {} // static layout
        }
    }

    override fun getItemCount() = chatItems.size

    // ── Highlight ────────────────────────────────────────

    private fun highlightText(content: String, originalIndex: Int): CharSequence {
        if (searchQuery.isBlank()) return content
        val spannable = SpannableString(content)
        val lowerContent = content.lowercase(Locale.getDefault())
        val lowerQuery = searchQuery.lowercase(Locale.getDefault())
        val isActiveMatch = matchPositions.isNotEmpty() &&
                activeMatchPosition in matchPositions.indices &&
                matchPositions[activeMatchPosition] == originalIndex
        var startIndex = 0
        while (true) {
            val index = lowerContent.indexOf(lowerQuery, startIndex)
            if (index == -1) break
            val bgColor = if (isActiveMatch) ACTIVE_HIGHLIGHT_COLOR else HIGHLIGHT_COLOR
            spannable.setSpan(BackgroundColorSpan(bgColor), index, index + lowerQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(Color.BLACK), index, index + lowerQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            startIndex = index + lowerQuery.length
        }
        return spannable
    }

    private fun formatTime(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = parser.parse(dateStr)
            if (date != null) formatter.format(date) else dateStr
        } catch (e: Exception) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = parser.parse(dateStr)
                if (date != null) formatter.format(date) else dateStr
            } catch (_: Exception) { dateStr }
        }
    }

    private fun bindAttachment(message: Message, ivAttachment: ImageView?, layoutDocBadge: LinearLayout?, tvDocName: TextView?, tvContent: TextView) {
        val hasAttachment = !message.attachment_url.isNullOrEmpty()
        val isImage = message.attachment_type == "image"
        if (hasAttachment && isImage) {
            ivAttachment?.visibility = View.VISIBLE
            layoutDocBadge?.visibility = View.GONE
            loadImageAsync(ivAttachment, message.attachment_url!!)
        } else if (hasAttachment) {
            ivAttachment?.visibility = View.GONE
            layoutDocBadge?.visibility = View.VISIBLE
            tvDocName?.text = if (message.attachment_type == "pdf") "PDF" else "Documento"
        } else {
            ivAttachment?.visibility = View.GONE
            layoutDocBadge?.visibility = View.GONE
        }
        tvContent.visibility = if (message.content.isBlank()) View.GONE else View.VISIBLE
    }

    private fun loadImageAsync(imageView: ImageView?, url: String) {
        if (imageView == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = BitmapFactory.decodeStream(java.net.URL(url).openStream())
                withContext(Dispatchers.Main) { imageView.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }
    }

    // ── ViewHolders ──────────────────────────────────────

    inner class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val tvReadStatus: TextView = itemView.findViewById(R.id.tvReadStatus)
        private val ivAttachment: ImageView? = itemView.findViewById(R.id.ivAttachment)
        private val layoutDocBadge: LinearLayout? = itemView.findViewById(R.id.layoutDocBadge)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)

        fun bind(message: Message, originalIndex: Int) {
            tvContent.text = highlightText(message.content, originalIndex)
            tvTime.text = formatTime(message.created_at)
            tvReadStatus.text = if (message.read_by?.isNotEmpty() == true) " ✓✓" else " ✓"
            bindAttachment(message, ivAttachment, layoutDocBadge, tvDocName, tvContent)
        }
    }

    inner class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val ivAttachment: ImageView? = itemView.findViewById(R.id.ivAttachment)
        private val layoutDocBadge: LinearLayout? = itemView.findViewById(R.id.layoutDocBadge)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)

        fun bind(message: Message, originalIndex: Int) {
            tvContent.text = highlightText(message.content, originalIndex)
            tvTime.text = formatTime(message.created_at)
            bindAttachment(message, ivAttachment, layoutDocBadge, tvDocName, tvContent)
        }
    }

    class DateDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateLabel: TextView = itemView.findViewById(R.id.tvDateLabel)
        fun bind(label: String) { tvDateLabel.text = label }
    }

    class UnreadDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
