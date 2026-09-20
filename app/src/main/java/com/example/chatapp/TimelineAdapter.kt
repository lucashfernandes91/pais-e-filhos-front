package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAdapter(
    private var items: List<TimelineItem> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val inputFormatAlt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)
    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val dayFormat = SimpleDateFormat("dd", ptBr)
    private val monthFormat = SimpleDateFormat("MMMM", ptBr)
    private val timeFormat = SimpleDateFormat("HH:mm", ptBr)

    fun updateItems(newItems: List<TimelineItem>) {
        val diffResult = DiffUtil.calculateDiff(TimelineDiffCallback(items, newItems))
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is TimelineItem.MessageItem -> VIEW_TYPE_MESSAGE
        is TimelineItem.EventItem -> VIEW_TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timeline_message, parent, false)
                MessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timeline_event, parent, false)
                EventViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageViewHolder -> holder.bind(items[position] as TimelineItem.MessageItem)
            is EventViewHolder -> holder.bind(items[position] as TimelineItem.EventItem)
        }
    }

    override fun getItemCount() = items.size

    private fun parseDate(dateStr: String): java.util.Date? {
        return try {
            inputFormat.parse(dateStr)
        } catch (e: Exception) {
            try { inputFormatAlt.parse(dateStr) } catch (e2: Exception) { null }
        }
    }

    private fun formatDateTime(date: Date): String {
        val month = monthFormat.format(date).replaceFirstChar { it.titlecase(ptBr) }
        return "${dayFormat.format(date)} de $month \u00b7 ${timeFormat.format(date)}"
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val senderName: TextView = itemView.findViewById(R.id.senderName)
        private val messageContent: TextView = itemView.findViewById(R.id.messageContent)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(item: TimelineItem.MessageItem) {
            val message = item.message

            val senderDisplay = message.sender.replaceFirstChar { it.uppercase() }
            senderName.text = senderDisplay
            messageContent.text =
                itemView.context.getString(R.string.timeline_sender_message, senderDisplay, message.content)

            val date = parseDate(message.created_at)
            if (date != null) {
                messageTime.text = formatDateTime(date)
            } else {
                messageTime.text = message.created_at
            }

            itemView.contentDescription = itemView.context.getString(
                R.string.timeline_message_content_description,
                senderDisplay,
                messageTime.text,
                message.content
            )
        }
    }

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        private val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        private val eventType: TextView = itemView.findViewById(R.id.eventType)
        private val eventCreator: TextView = itemView.findViewById(R.id.eventCreator)
        private val eventNotes: TextView = itemView.findViewById(R.id.eventNotes)
        private val timelineDotIcon: ImageView = itemView.findViewById(R.id.timelineDotIcon)

        fun bind(item: TimelineItem.EventItem) {
            val event = item.event
            eventTitle.text = event.title

            val date = parseDate(event.event_date)
            if (date != null) {
                eventDate.text = formatDateTime(date)
            } else {
                eventDate.text = event.event_date
            }

            // Ícone na timeline conforme tipo de evento
            timelineDotIcon.setImageResource(AppEventType.fromRaw(event.event_type).iconRes)

            eventType.visibility = View.GONE
            eventCreator.visibility = View.GONE

            if (event.notes.isNotEmpty()) {
                eventNotes.text = event.notes
                eventNotes.visibility = View.VISIBLE
            } else {
                eventNotes.visibility = View.GONE
            }

            itemView.contentDescription = itemView.context.getString(
                R.string.timeline_event_content_description,
                event.title,
                eventDate.text,
                event.notes.ifBlank {
                    itemView.context.getString(R.string.timeline_event_without_notes)
                }
            )
        }
    }

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_EVENT = 1
    }

    private class TimelineDiffCallback(
        private val oldItems: List<TimelineItem>,
        private val newItems: List<TimelineItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldItems[oldItemPosition]
            val newItem = newItems[newItemPosition]

            return when {
                oldItem is TimelineItem.MessageItem && newItem is TimelineItem.MessageItem ->
                    oldItem.message.id == newItem.message.id
                oldItem is TimelineItem.EventItem && newItem is TimelineItem.EventItem ->
                    oldItem.event.id == newItem.event.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}
