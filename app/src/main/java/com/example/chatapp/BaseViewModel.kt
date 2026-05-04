package com.example.chatapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Base ViewModel with common error handling
 */
abstract class BaseViewModel : ViewModel() {

    val isLoading = MutableLiveData<Boolean>(false)
    val error = MutableLiveData<String?>(null)
    val showRetryButton = MutableLiveData<Boolean>(false)

    /**
     * Safely execute a suspend function with error handling
     */
    protected suspend fun <T> safeCall(
        call: suspend () -> T
    ): T? {
        return try {
            isLoading.postValue(true)
            error.postValue(null)
            showRetryButton.postValue(false)
            call()
        } catch (e: CancellationException) {
            // Don't log cancellations
            null
        } catch (e: IOException) {
            handleError("Conexão perdida. Verifique sua internet")
            null
        } catch (e: Exception) {
            handleError("Erro ao carregar: ${e.message ?: "Desconhecido"}")
            null
        } finally {
            isLoading.postValue(false)
        }
    }

    /**
     * Handle errors and show user message
     */
    protected fun handleError(message: String) {
        error.postValue(message)
        showRetryButton.postValue(true)
    }

    /**
     * Clear error state
     */
    fun clearError() {
        error.postValue(null)
        showRetryButton.postValue(false)
    }
}
