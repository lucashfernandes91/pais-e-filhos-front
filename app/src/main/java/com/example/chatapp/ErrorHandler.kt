package com.example.chatapp

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import android.view.View
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Handle API exceptions and show user-friendly messages
     */
    fun handleException(
        context: Context,
        exception: Exception,
        onRetry: (() -> Unit)? = null
    ): String {
        Log.e(TAG, "Exception: ${exception.javaClass.simpleName}: ${exception.message}", exception)

        val message = when (exception) {
            // Network errors
            is ConnectException -> "Sem conexão com o servidor"
            is SocketTimeoutException -> "Tempo de conexão expirou. Tente novamente"
            is IOException -> "Erro de conexão. Verifique sua internet"

            // HTTP errors
            is HttpException -> handleHttpException(exception)

            // Other errors
            else -> "Erro inesperado: ${exception.localizedMessage ?: "Desconhecido"}"
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return message
    }

    /**
     * Handle HTTP exceptions with specific status codes
     */
    private fun handleHttpException(exception: HttpException): String {
        return when (exception.code()) {
            400 -> "Dados inválidos. Verifique e tente novamente"
            401 -> "Sessão expirada. Faça login novamente"
            403 -> "Você não tem permissão para isso"
            404 -> "Recurso não encontrado"
            409 -> "Conflito de dados. Tente novamente"
            429 -> "Muitas requisições. Aguarde um momento"
            500, 502, 503 -> "Servidor indisponível. Tente novamente em alguns momentos"
            else -> "Erro ${exception.code()}: ${exception.message()}"
        }
    }

    /**
     * Show error with retry action
     */
    fun showErrorWithRetry(
        view: View,
        message: String,
        onRetry: () -> Unit
    ) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction("Tentar novamente") { onRetry() }
            .show()
    }

    /**
     * Log error to backend (optional)
     */
    fun logErrorToBackend(
        exception: Exception,
        context: String = "Unknown"
    ) {
        val errorLog = mapOf(
            "context" to context,
            "exception" to exception.javaClass.simpleName,
            "message" to (exception.message ?: "No message"),
            "timestamp" to System.currentTimeMillis()
        )

        Log.e(TAG, "Error Log: $errorLog")
        // TODO: Send to backend error tracking service
    }
}
