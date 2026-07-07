package com.example.chatapp

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
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
        @Suppress("UNUSED_PARAMETER") onRetry: (() -> Unit)? = null
    ): String {
        Log.e(TAG, "Exception: ${exception.javaClass.simpleName}: ${exception.message}", exception)

        val message = when (exception) {
            is ConnectException -> context.getString(R.string.error_no_server_connection)
            is SocketTimeoutException -> context.getString(R.string.error_connection_timeout)
            is IOException -> context.getString(R.string.error_check_internet)
            is HttpException -> handleHttpException(context, exception)
            else -> context.getString(
                R.string.error_unexpected,
                exception.localizedMessage ?: context.getString(R.string.error_unknown)
            )
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return message
    }

    /**
     * Handle HTTP exceptions with specific status codes
     */
    private fun handleHttpException(context: Context, exception: HttpException): String {
        return when (exception.code()) {
            400 -> context.getString(R.string.error_invalid_data)
            401 -> context.getString(R.string.error_session_expired_login)
            403 -> context.getString(R.string.error_no_permission)
            404 -> context.getString(R.string.error_resource_not_found)
            409 -> context.getString(R.string.error_data_conflict)
            429 -> context.getString(R.string.error_too_many_requests)
            500, 502, 503 -> context.getString(R.string.error_server_unavailable)
            else -> context.getString(
                R.string.error_http_generic,
                exception.code(),
                exception.message().orEmpty()
            )
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
            .setAction(R.string.action_try_again) { onRetry() }
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
