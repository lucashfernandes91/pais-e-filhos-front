package com.example.chatapp

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val context: Context,
    private val conversationId: Int
) : ViewModel() {

    companion object {
        // Deve acompanhar o page size padrão do backend (list_messages).
        const val PAGE_SIZE = 100
    }

    val items = MutableLiveData<List<TimelineItem>>()
    val isLoading = MutableLiveData<Boolean>()
    val error = MutableLiveData<String?>()

    private val currentItems = mutableListOf<TimelineItem>()
    private var oldestMessageId: Int? = null
    private var hasMoreMessages = false
    private var isFetchingOlder = false

    private fun currentBearerToken(): String? {
        val token = PrefsHelper.getAuthToken(context)
        if (token.isEmpty()) {
            error.postValue("Erro ao carregar: sessão indisponível")
            return null
        }
        return "Bearer $token"
    }

    fun loadTimeline() {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                error.postValue(null)

                val bearerToken = currentBearerToken() ?: return@launch
                val messages = RetrofitClient.api.getMessages(bearerToken, conversationId)
                val events = RetrofitClient.api.getEvents(bearerToken, conversationId)

                hasMoreMessages = messages.size >= PAGE_SIZE
                oldestMessageId = messages.minByOrNull { it.id }?.id

                currentItems.clear()
                currentItems.addAll(messages.map { TimelineItem.MessageItem(it) })
                currentItems.addAll(events.map { TimelineItem.EventItem(it) })
                currentItems.sortByDescending { it.getDate() }

                items.postValue(currentItems.toList())
            } catch (e: Exception) {
                error.postValue("Erro ao carregar: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    /** Carrega a página anterior de mensagens ao chegar no fim da lista. */
    fun loadOlderMessages() {
        if (isFetchingOlder || !hasMoreMessages) return
        val before = oldestMessageId ?: return

        isFetchingOlder = true
        viewModelScope.launch {
            try {
                val older = RetrofitClient.api.getMessages(
                    currentBearerToken() ?: return@launch, conversationId, before = before
                )
                hasMoreMessages = older.size >= PAGE_SIZE
                if (older.isNotEmpty()) {
                    oldestMessageId = older.minByOrNull { it.id }?.id
                    currentItems.addAll(older.map { TimelineItem.MessageItem(it) })
                    currentItems.sortByDescending { it.getDate() }
                    items.postValue(currentItems.toList())
                }
            } catch (_: Exception) {
                // Silencioso: rolar ao fim novamente refaz a tentativa.
            } finally {
                isFetchingOlder = false
            }
        }
    }
}

class TimelineViewModelFactory(
    context: Context,
    private val conversationId: Int
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TimelineViewModel(appContext, conversationId) as T
    }
}
