package com.example.chatapp

data class Message(
    val id: Int,
    val sender: String,
    val content: String,
    val created_at: String,
    val conversation: Int,
    val read_by: List<MessageRead>? = null,
    val attachment_url: String? = null,
    val attachment_type: String? = null
)
