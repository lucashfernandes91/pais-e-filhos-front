package com.example.chatapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPagingItemMapperTest {
    private val user = "lucas"

    @Test
    fun refreshAppendAndPrependMergeAreIdempotent() {
        val firstPage = listOf(message(1, "2026-09-27T10:00:00Z", "old"))
        val appended = listOf(
            message(2, "2026-09-28T10:00:00Z", "new"),
            message(2, "2026-09-28T10:00:00Z", "new")
        )
        val prepended = listOf(message(1, "2026-09-27T10:00:00Z", "old"))

        val result = ChatPagingItemMapper.mergeMessages(
            ChatPagingItemMapper.mergeMessages(firstPage, appended),
            prepended
        )

        assertEquals(listOf(1, 2), result.map { it.id })
        assertEquals(listOf("old", "new"), result.map { it.content })
    }

    @Test
    fun dateSeparatorIsInsertedOnlyWhenDayChanges() {
        val before = ChatPagingItemMapper.item(
            message(1, "2026-09-28T10:00:00Z", "one"),
            emptyList()
        )
        val afterSameDay = ChatPagingItemMapper.item(
            message(2, "2026-09-28T11:00:00Z", "two"),
            emptyList()
        )
        val afterNextDay = ChatPagingItemMapper.item(
            message(3, "2026-09-29T11:00:00Z", "three"),
            emptyList()
        )

        assertEquals(null, ChatPagingItemMapper.dateSeparator(before, afterSameDay))
        assertTrue(ChatPagingItemMapper.dateSeparator(before, afterNextDay) is ChatItem.DateDivider)
    }

    @Test
    fun unreadSeparatorAppearsAtFirstUnreadReceivedMessage() {
        val read = message(1, "2026-09-28T10:00:00Z", "read", sender = "other")
            .copy(read_by = listOf(MessageRead(user, "2026-09-28T10:01:00Z")))
        val unread = message(2, "2026-09-28T11:00:00Z", "unread", sender = "other")
        val before = ChatPagingItemMapper.item(read, emptyList())
        val after = ChatPagingItemMapper.item(unread, emptyList())

        assertTrue(
            ChatPagingItemMapper.unreadSeparator(user, before, after) is ChatItem.UnreadDivider
        )
    }

    @Test
    fun pendingClientIdReplacesDuplicateServerMessage() {
        val pending = message(
            -1,
            "2026-09-28T10:00:00Z",
            "hello",
            clientMessageId = "client-1"
        )
        val server = message(
            10,
            "2026-09-28T10:00:01Z",
            "hello",
            clientMessageId = "client-1"
        )

        val merged = ChatPagingItemMapper.mergeMessages(listOf(pending), listOf(server))

        assertEquals(1, merged.size)
        assertEquals(10, merged.single().id)
    }

    private fun message(
        id: Int,
        createdAt: String,
        content: String,
        sender: String = user,
        clientMessageId: String? = null
    ) = Message(
        id = id,
        sender = sender,
        content = content,
        created_at = createdAt,
        conversation = 7,
        client_message_id = clientMessageId
    )
}
