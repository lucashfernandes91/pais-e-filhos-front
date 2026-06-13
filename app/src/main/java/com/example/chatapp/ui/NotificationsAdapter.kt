package com.example.chatapp.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationsAdapter(
    private val items: MutableList<NotificationItem>,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    /**
     * Replaces the entire list with new data using DiffUtil for efficient updates.
     */
    fun updateItems(newItems: List<NotificationItem>) {
        val diffResult = DiffUtil.calculateDiff(NotificationDiffCallback(items, newItems))
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Marks all items as read locally (UI-only, call after backend confirms).
     */
    fun markAllAsRead() {
        val updatedItems = items.map { it.copy(isRead = true) }
        updateItems(updatedItems)
    }

    fun clearItems() {
        updateItems(emptyList())
    }

    fun hasUnreadItems(): Boolean = items.any { !it.isRead }

    /**
     * Marks a single item as read by ID.
     */
    fun markAsRead(notificationId: String) {
        val index = items.indexOfFirst { it.id == notificationId }
        if (index != -1) {
            items[index] = items[index].copy(isRead = true)
            notifyItemChanged(index)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconFrame: FrameLayout = itemView.findViewById(R.id.iconFrame)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvNotifMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvNotifTime)
        private val dotUnread: View = itemView.findViewById(R.id.dotUnread)

        fun bind(item: NotificationItem, onItemClick: (NotificationItem) -> Unit) {
            tvTitle.text = item.title
            tvMessage.text = item.message
            tvTime.text = formatTimeAgo(item.timestamp)

            // Unread indicator
            dotUnread.visibility = if (item.isRead) View.GONE else View.VISIBLE
            tvTitle.setTypeface(null, if (item.isRead) Typeface.NORMAL else Typeface.BOLD)

            // Icon & color por tipo
            val (iconRes, bgRes) = when (item.type) {
                NotificationType.MESSAGE -> Pair(R.drawable.ic_chat_outline, R.drawable.bg_icon_circle_blue)
                NotificationType.EVENT -> Pair(R.drawable.ic_calendar_add_outline, R.drawable.bg_icon_circle_green)
                NotificationType.CUSTODY -> Pair(R.drawable.ic_custody, R.drawable.bg_icon_circle_blue)
                NotificationType.SYSTEM -> Pair(R.drawable.ic_info_circle, R.drawable.bg_icon_circle_blue)
            }

            val tintColor = when (item.type) {
                NotificationType.EVENT -> R.color.secondary_green
                else -> R.color.primary_blue
            }

            iconFrame.background = ContextCompat.getDrawable(itemView.context, bgRes)
            ivIcon.setImageResource(iconRes)
            ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, tintColor))

            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun formatTimeAgo(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)

            return when {
                minutes < 1 -> "Agora"
                minutes < 60 -> "${minutes}min"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> {
                    val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    /**
     * DiffUtil callback for efficient RecyclerView updates.
     * Avoids full notifyDataSetChanged() — only updates changed items.
     */
    private class NotificationDiffCallback(
        private val oldList: List<NotificationItem>,
        private val newList: List<NotificationItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos].id == newList[newPos].id
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos] == newList[newPos]
        }
    }
}
