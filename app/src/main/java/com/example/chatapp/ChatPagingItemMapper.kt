package com.example.chatapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ChatPagingItemMapper {
    fun item(message: Message, allMessages: List<Message>): ChatItem.MessageItem =
        ChatItem.MessageItem(message, allMessages.indexOfFirst { key(it) == key(message) })

    fun dateSeparator(before: ChatItem?, after: ChatItem?): ChatItem? {
        val afterMessage = (after as? ChatItem.MessageItem)?.message ?: return null
        val beforeMessage = (before as? ChatItem.MessageItem)?.message
        if (before == null || dateKey(beforeMessage) != dateKey(afterMessage)) {
            return ChatItem.DateDivider(dateLabel(afterMessage))
        }
        return null
    }

    fun unreadSeparator(
        currentUsername: String,
        before: ChatItem?,
        after: ChatItem?
    ): ChatItem? {
        val afterMessage = (after as? ChatItem.MessageItem)?.message ?: return null
        val beforeMessage = (before as? ChatItem.MessageItem)?.message
        val afterUnread = afterMessage.sender != currentUsername &&
            afterMessage.id > 0 &&
            afterMessage.read_by?.any { it.reader_name == currentUsername } != true
        val beforeUnread = beforeMessage?.let {
            it.sender != currentUsername &&
                it.id > 0 &&
                it.read_by?.any { read -> read.reader_name == currentUsername } != true
        } == true
        return if (afterUnread && !beforeUnread) ChatItem.UnreadDivider else null
    }

    fun mergeMessages(
        existing: List<Message>,
        incoming: List<Message>
    ): List<Message> {
        val merged = LinkedHashMap<String, Message>()
        (existing + incoming).forEach { merged[key(it)] = it }
        return merged.values.sortedWith(compareBy<Message> { it.created_at }.thenBy { key(it) })
    }

    fun key(message: Message): String =
        message.client_message_id?.takeIf { it.isNotBlank() }
            ?: if (message.id > 0) "server:${message.id}" else {
                "local:${message.created_at}:${message.content}"
            }

    private fun dateKey(message: Message?): String =
        message?.let {
            AppDateTime.parseApi(it.created_at)?.let { date ->
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            }.orEmpty()
        }.orEmpty()

    private fun dateLabel(message: Message): String {
        val key = dateKey(message)
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
}
