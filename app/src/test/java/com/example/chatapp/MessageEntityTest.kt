package com.example.chatapp

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageEntityTest {
    @Test
    fun preservesMessageFieldsThroughRoomMapping() {
        val message = Message(
            id = 42,
            sender = "lucas",
            content = "Olá",
            created_at = "2026-09-28T12:00:00-03:00",
            conversation = 7,
            read_by = listOf(
                MessageRead(reader_name = "lucas", read_at = "2026-09-28T15:00:00Z")
            ),
            attachment_url = "https://example.test/file.jpg",
            attachment_type = "image/jpeg",
            attachment_name = "file.jpg",
            client_message_id = "client-42"
        )

        assertEquals(message, MessageEntity.fromMessage(message).toMessage())
    }
}
