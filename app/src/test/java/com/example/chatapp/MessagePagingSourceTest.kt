package com.example.chatapp

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagePagingSourceTest {
    @Test
    fun refreshAppendAndPrependLoadExpectedWindows() = runBlocking {
        val source = InMemoryMessagePagingSource((1..5).map { message(it) })

        val refresh = source.load(
            PagingSource.LoadParams.Refresh<Int>(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        ) as PagingSource.LoadResult.Page
        assertEquals(listOf(1, 2), refresh.data.map { it.id })
        assertEquals(null, refresh.prevKey)
        assertEquals(2, refresh.nextKey)

        val append = source.load(
            PagingSource.LoadParams.Append(
                key = refresh.nextKey!!,
                loadSize = 2,
                placeholdersEnabled = false
            )
        ) as PagingSource.LoadResult.Page
        assertEquals(listOf(3, 4), append.data.map { it.id })
        assertEquals(4, append.nextKey)

        val prepend = source.load(
            PagingSource.LoadParams.Prepend(
                key = 2,
                loadSize = 2,
                placeholdersEnabled = false
            )
        ) as PagingSource.LoadResult.Page
        assertEquals(listOf(3, 4), prepend.data.map { it.id })
        assertEquals(0, prepend.prevKey)
    }

    private class InMemoryMessagePagingSource(
        private val messages: List<Message>
    ) : PagingSource<Int, Message>() {
        override fun getRefreshKey(state: PagingState<Int, Message>): Int? = null

        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, Message> {
            val start = params.key ?: 0
            val end = (start + params.loadSize).coerceAtMost(messages.size)
            val data = messages.subList(start, end)
            return LoadResult.Page(
                data = data,
                prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
                nextKey = if (end == messages.size) null else end
            )
        }
    }

    private fun message(id: Int) = Message(
        id = id,
        sender = "other",
        content = "message-$id",
        created_at = "2026-09-28T10:0${id}:00Z",
        conversation = 7
    )
}
