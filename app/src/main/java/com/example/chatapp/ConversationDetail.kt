package com.example.chatapp

data class ConversationDetail(
    val id: Int,
    val created_at: String,
    val participants: List<Participant>,
    val children: List<Child>
)

data class Participant(
    val id: Int,
    val username: String,
    val is_me: Boolean
)
