package com.example.chatapp

import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.flow.Flow

class MessageRepository(
    context: android.content.Context,
    private val api: ApiService = RetrofitClient.api
) {
    private val dao = AppDatabase.get(context).messageDao()

    fun observeConversation(conversationId: Int): Flow<List<MessageEntity>> =
        dao.observeConversation(conversationId)

    fun pagingMessages(conversationId: Int) =
        Pager(PagingConfig(pageSize = ChatViewModel.PAGE_SIZE)) {
            dao.pagingSource(conversationId)
        }.flow

    suspend fun refresh(conversationId: Int, token: String): List<Message> {
        val remote = api.getMessages(token, conversationId)
        dao.upsertAll(remote.map(MessageEntity::fromMessage))
        return remote
    }

    suspend fun cache(messages: List<Message>) {
        if (messages.isNotEmpty()) dao.upsertAll(messages.map(MessageEntity::fromMessage))
    }
}
