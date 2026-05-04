package com.example.chatapp

import android.content.Context
import android.widget.Toast
import android.view.View
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
        actionText: String = "Tentar novamente",
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
        title: String = "Erro",
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok") { dialog, _ ->
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
            .setTitle("Erro")
            .setMessage(message)
            .setPositiveButton("Tentar novamente") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
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
            "Não foi possível conectar ao servidor.\n\nVerifique sua conexão de internet.",
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
            .setTitle("Sessão expirada")
            .setMessage("Sua sessão expirou. Por favor, faça login novamente.")
            .setPositiveButton("Ir para Login") { dialog, _ ->
                dialog.dismiss()
                onLogin()
            }
            .setCancelable(false)
            .show()
    }
}
