package com.example.chatapp

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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagingDataAdapter
import java.text.SimpleDateFormat
import java.util.Locale

class MessageAdapter(
    private val currentUsername: String = "current_user",
    private val onImageClick: (String) -> Unit = {},
    private val onDocumentClick: (String, String) -> Unit = { _, _ -> }
) : PagingDataAdapter<ChatItem, RecyclerView.ViewHolder>(CHAT_ITEM_DIFF) {

    private var searchQuery: String = ""
    private var activeMatchPosition: Int = -1
    private var matchPositions: List<Int> = emptyList()

    fun updateSearch(query: String, activePos: Int, matches: List<Int>) {
        val previousQuery = searchQuery
        val previousActiveMatchPosition = activeMatchPosition
        val previousMatchPositions = matchPositions

        searchQuery = query
        activeMatchPosition = activePos
        matchPositions = matches

        if (
            previousQuery == query &&
            previousActiveMatchPosition == activePos &&
            previousMatchPositions == matches
        ) {
            return
        }

        val impactedOriginalIndexes = buildSet {
            addAll(previousMatchPositions)
            addAll(matches)
            previousMatchPositions.getOrNull(previousActiveMatchPosition)?.let(::add)
            matches.getOrNull(activePos)?.let(::add)
        }

        if (impactedOriginalIndexes.isEmpty()) {
            return
        }

        snapshot().items.forEachIndexed { adapterPosition, item ->
            if (item is ChatItem.MessageItem && item.originalIndex in impactedOriginalIndexes) {
                notifyItemChanged(adapterPosition)
            }
        }
    }

    fun getAdapterPositionForMessage(originalIndex: Int): Int {
        return snapshot().items.indexOfFirst { item ->
            item is ChatItem.MessageItem && item.originalIndex == originalIndex
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            null -> VIEW_TYPE_UNREAD_DIVIDER
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
            VIEW_TYPE_SENT -> SentViewHolder(
                inflater.inflate(R.layout.item_message_sent, parent, false)
            )
            VIEW_TYPE_RECEIVED -> ReceivedViewHolder(
                inflater.inflate(R.layout.item_message_received, parent, false)
            )
            VIEW_TYPE_DATE_DIVIDER -> DateDividerViewHolder(
                inflater.inflate(R.layout.item_date_divider, parent, false)
            )
            VIEW_TYPE_UNREAD_DIVIDER -> UnreadDividerViewHolder(
                inflater.inflate(R.layout.item_unread_divider, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            null -> return
            is ChatItem.MessageItem -> {
                when (holder) {
                    is SentViewHolder -> holder.bind(item.message, item.originalIndex)
                    is ReceivedViewHolder -> holder.bind(item.message, item.originalIndex)
                }
            }
            is ChatItem.DateDivider -> (holder as DateDividerViewHolder).bind(item.dateLabel)
            is ChatItem.UnreadDivider -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SentViewHolder -> holder.clearAttachment()
            is ReceivedViewHolder -> holder.clearAttachment()
        }
        super.onViewRecycled(holder)
    }

    private fun highlightText(
        context: android.content.Context,
        content: String,
        originalIndex: Int
    ): CharSequence {
        if (searchQuery.isBlank()) {
            return content
        }

        val highlight = androidx.core.content.ContextCompat.getColor(context, R.color.search_highlight)
        val activeHighlight = androidx.core.content.ContextCompat.getColor(context, R.color.search_highlight_active)
        val highlightText = androidx.core.content.ContextCompat.getColor(context, R.color.search_highlight_text)

        val spannable = SpannableString(content)
        val lowerContent = content.lowercase(Locale.getDefault())
        val lowerQuery = searchQuery.lowercase(Locale.getDefault())
        val activeOriginalIndex = matchPositions.getOrNull(activeMatchPosition)
        val isActiveMatch = activeOriginalIndex == originalIndex

        var startIndex = 0
        while (true) {
            val index = lowerContent.indexOf(lowerQuery, startIndex)
            if (index == -1) {
                break
            }

            val bgColor = if (isActiveMatch) activeHighlight else highlight
            spannable.setSpan(
                BackgroundColorSpan(bgColor),
                index,
                index + lowerQuery.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(highlightText),
                index,
                index + lowerQuery.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + lowerQuery.length
        }

        return spannable
    }

    private fun formatTime(dateStr: String): String {
        return try {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = AppDateTime.parseApi(dateStr)
            if (date != null) formatter.format(date) else dateStr
        } catch (_: Exception) {
            dateStr
        }
    }

    private fun bindAttachment(
        message: Message,
        ivAttachment: ImageView?,
        layoutDocBadge: LinearLayout?,
        tvDocName: TextView?,
        tvContent: TextView
    ) {
        val hasAttachment = !message.attachment_url.isNullOrEmpty()
        val isImage = message.attachment_type == "image"

        if (hasAttachment && isImage) {
            ivAttachment?.visibility = View.VISIBLE
            layoutDocBadge?.visibility = View.GONE
            if (ivAttachment != null) {
                AttachmentImageLoader.load(ivAttachment, message.attachment_url!!)
                ivAttachment.setOnClickListener { onImageClick(message.attachment_url!!) }
            }
        } else if (hasAttachment) {
            AttachmentImageLoader.clear(ivAttachment)
            ivAttachment?.setOnClickListener(null)
            ivAttachment?.visibility = View.GONE
            layoutDocBadge?.visibility = View.VISIBLE
            val fileName = message.attachment_name?.takeIf { it.isNotBlank() }
                ?: if (message.attachment_type == "pdf") "documento.pdf" else "Documento"
            layoutDocBadge?.setOnClickListener {
                onDocumentClick(message.attachment_url!!, fileName)
            }
            tvDocName?.text = fileName
        } else {
            AttachmentImageLoader.clear(ivAttachment)
            ivAttachment?.setOnClickListener(null)
            layoutDocBadge?.setOnClickListener(null)
            ivAttachment?.visibility = View.GONE
            layoutDocBadge?.visibility = View.GONE
        }

        tvContent.visibility = if (message.content.isBlank()) View.GONE else View.VISIBLE
    }

    private fun buildMessageAnnouncement(
        context: android.content.Context,
        message: Message,
        timeText: String
    ): String {
        val contentSummary = message.content.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.chat_message_attachment_only)

        return if (message.sender == currentUsername) {
            val readStatusText = context.getString(
                if (message.read_by?.isNotEmpty() == true) {
                    R.string.chat_message_read_status_read
                } else {
                    R.string.chat_message_read_status_delivered
                }
            )
            context.getString(
                R.string.cd_message_bubble_sent,
                timeText,
                contentSummary,
                readStatusText
            )
        } else {
            context.getString(
                R.string.cd_message_bubble_received,
                message.sender.replaceFirstChar { it.uppercase() },
                timeText,
                contentSummary
            )
        }
    }

    inner class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val tvReadStatus: TextView = itemView.findViewById(R.id.tvReadStatus)
        private val ivAttachment: ImageView? = itemView.findViewById(R.id.ivAttachment)
        private val layoutDocBadge: LinearLayout? = itemView.findViewById(R.id.layoutDocBadge)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)

        fun bind(message: Message, originalIndex: Int) {
            tvContent.text = highlightText(tvContent.context, message.content, originalIndex)
            tvTime.text = formatTime(message.created_at)
            tvReadStatus.text = if (message.read_by?.isNotEmpty() == true) " \u2713\u2713" else " \u2713"
            tvReadStatus.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            bindAttachment(message, ivAttachment, layoutDocBadge, tvDocName, tvContent)
            itemView.contentDescription = buildMessageAnnouncement(
                itemView.context,
                message,
                tvTime.text.toString()
            )
        }

        fun clearAttachment() {
            AttachmentImageLoader.clear(ivAttachment)
        }
    }

    inner class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvMessageContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val ivAttachment: ImageView? = itemView.findViewById(R.id.ivAttachment)
        private val layoutDocBadge: LinearLayout? = itemView.findViewById(R.id.layoutDocBadge)
        private val tvDocName: TextView? = itemView.findViewById(R.id.tvDocName)

        fun bind(message: Message, originalIndex: Int) {
            tvContent.text = highlightText(tvContent.context, message.content, originalIndex)
            tvTime.text = formatTime(message.created_at)
            bindAttachment(message, ivAttachment, layoutDocBadge, tvDocName, tvContent)
            itemView.contentDescription = buildMessageAnnouncement(
                itemView.context,
                message,
                tvTime.text.toString()
            )
        }

        fun clearAttachment() {
            AttachmentImageLoader.clear(ivAttachment)
        }
    }

    class DateDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateLabel: TextView = itemView.findViewById(R.id.tvDateLabel)

        fun bind(label: String) {
            tvDateLabel.text = label
        }
    }

    class UnreadDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_DATE_DIVIDER = 2
        private const val VIEW_TYPE_UNREAD_DIVIDER = 3
        private val CHAT_ITEM_DIFF = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
                return when {
                    oldItem is ChatItem.MessageItem && newItem is ChatItem.MessageItem ->
                        messageKey(oldItem.message) == messageKey(newItem.message)
                    oldItem is ChatItem.DateDivider && newItem is ChatItem.DateDivider ->
                        oldItem.dateLabel == newItem.dateLabel
                    oldItem is ChatItem.UnreadDivider && newItem is ChatItem.UnreadDivider -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
                oldItem == newItem

            private fun messageKey(message: Message): String =
                message.client_message_id?.takeIf { it.isNotBlank() }
                    ?: if (message.id > 0) "server:${message.id}" else {
                        "local:${message.created_at}:${message.content}"
                    }
        }

        private fun messageKey(message: Message): String =
            message.client_message_id?.takeIf { it.isNotBlank() }
                ?: if (message.id > 0) "server:${message.id}" else {
                    "local:${message.created_at}:${message.content}"
                }
    }
}
