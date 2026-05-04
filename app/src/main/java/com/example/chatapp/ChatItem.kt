package com.example.chatapp

sealed class ChatItem {
    data class MessageItem(val message: Message, val originalIndex: Int) : ChatItem()
    data class DateDivider(val dateLabel: String) : ChatItem()
    object UnreadDivider : ChatItem()
}
