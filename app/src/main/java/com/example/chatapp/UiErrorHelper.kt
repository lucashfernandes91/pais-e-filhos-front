package com.example.chatapp

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar

object UiErrorHelper {

    /**
     * Show error as toast (short duration)
     */
    fun showErrorToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show error as snackbar with action
     */
    fun showErrorSnackbar(
        view: View,
        message: String,
        actionText: String = view.context.getString(R.string.action_try_again),
        onAction: (() -> Unit)? = null
    ) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).apply {
            if (onAction != null) {
                setAction(actionText) { onAction() }
            }
            show()
        }
    }

    /**
     * Show error dialog
     */
    fun showErrorDialog(
        context: Context,
        title: String = context.getString(R.string.ui_error_title),
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .show()
    }

    /**
     * Show validation error on EditText
     */
    fun showValidationError(view: View, message: String) {
        if (view is android.widget.EditText) {
            view.error = message
            view.requestFocus()
        }
    }

    /**
     * Show retry dialog
     */
    fun showRetryDialog(
        context: Context,
        message: String,
        onRetry: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.ui_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_try_again) { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                dialog.dismiss()
                onCancel?.invoke()
            }
            .show()
    }

    /**
     * Show network error with retry option
     */
    fun showNetworkError(
        context: Context,
        onRetry: () -> Unit
    ) {
        showRetryDialog(
            context,
            context.getString(R.string.ui_network_error_message),
            onRetry
        )
    }

    /**
     * Show session expired error
     */
    fun showSessionExpired(
        context: Context,
        onLogin: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.ui_session_expired_title)
            .setMessage(R.string.ui_session_expired_message)
            .setPositiveButton(R.string.ui_go_to_login) { dialog, _ ->
                dialog.dismiss()
                onLogin()
            }
            .setCancelable(false)
            .show()
    }
}
