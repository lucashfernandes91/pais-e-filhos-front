package com.example.chatapp

import com.google.gson.annotations.SerializedName

data class Event(
    val id: Int,
    val conversation: Int,
    val created_by_name: String,
    val title: String,
    val event_date: String,
    val event_date_end: String? = null,
    val event_type: String,
    val notes: String,
    val created_at: String,
    val updated_at: String? = null,
    val version: Int = 1
)

data class CreateEventRequest(
    val conversation_id: Int,
    val title: String,
    val event_date: String,
    val event_date_end: String? = null,
    val event_type: String,
    val notes: String = ""
)

data class EventFieldChange(
    val before: String? = null,
    val after: String? = null
)

data class EventChange(
    val id: Int,
    val event: Int? = null,
    val event_id_snapshot: Int,
    val conversation: Int,
    val actor_name: String,
    val action: String,
    val event_title: String,
    val changes: Map<String, EventFieldChange> = emptyMap(),
    val snapshot: Map<String, String?> = emptyMap(),
    val version: Int,
    val created_at: String
)

data class MessageRead(
    @SerializedName("reader_name")
    val reader_name: String,
    
    @SerializedName("read_at")
    val read_at: String
)
