package com.example.chatapp

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TimelineViewModel(private val token: String, private val conversationId: Int) : ViewModel() {

    val items = MutableLiveData<List<TimelineItem>>()
    val isLoading = MutableLiveData<Boolean>()
    val error = MutableLiveData<String?>()

    fun loadTimeline() {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                error.postValue(null)

                val messages = RetrofitClient.api.getMessages("Bearer $token", conversationId)
                val events = RetrofitClient.api.getEvents("Bearer $token", conversationId)

                val timelineItems = mutableListOf<TimelineItem>()
                timelineItems.addAll(messages.map { TimelineItem.MessageItem(it) })
                timelineItems.addAll(events.map { TimelineItem.EventItem(it) })

                timelineItems.sortByDescending { it.getDate() }

                items.postValue(timelineItems)
            } catch (e: Exception) {
                error.postValue("Erro ao carregar: ${e.message}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }
}

class TimelineViewModelFactory(
    private val token: String,
    private val conversationId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TimelineViewModel(token, conversationId) as T
    }
}
