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
    val created_at: String
)

data class CreateEventRequest(
    val conversation_id: Int,
    val title: String,
    val event_date: String,
    val event_date_end: String? = null,
    val event_type: String,
    val notes: String = ""
)

data class MessageRead(
    @SerializedName("reader_name")
    val reader_name: String,
    
    @SerializedName("read_at")
    val read_at: String
)
