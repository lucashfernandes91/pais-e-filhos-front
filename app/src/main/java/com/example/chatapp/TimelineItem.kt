package com.example.chatapp

sealed class TimelineItem {
    data class MessageItem(val message: Message) : TimelineItem()
    data class EventItem(val event: Event) : TimelineItem()

    fun getDate(): String = when (this) {
        is MessageItem -> message.created_at
        is EventItem -> event.event_date
    }
}
