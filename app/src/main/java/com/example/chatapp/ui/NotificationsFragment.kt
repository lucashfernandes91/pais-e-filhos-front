package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyState: View
    private lateinit var errorState: View
    private lateinit var notificationActions: View
    private lateinit var btnMarkAllRead: MaterialButton
    private lateinit var btnDeleteAll: MaterialButton
    private lateinit var btnRetry: View
    private lateinit var progressLoading: ProgressBar

    private var adapter: NotificationsAdapter? = null
    private var token: String = ""
    private var isActionRunning = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvNotifications = view.findViewById(R.id.rvNotifications)
        emptyState = view.findViewById(R.id.emptyState)
        errorState = view.findViewById(R.id.errorStateNotifications)
        notificationActions = view.findViewById(R.id.notificationActions)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        btnDeleteAll = view.findViewById(R.id.btnDeleteAllNotifications)
        btnRetry = view.findViewById(R.id.btnRetryNotifications)
        progressLoading = view.findViewById(R.id.progressLoading)

        token = PrefsHelper.getAuthToken(requireContext())

        // Voltar
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Marcar todas como lidas
        btnMarkAllRead.setOnClickListener {
            markAllAsRead()
        }
        btnDeleteAll.setOnClickListener {
            confirmDeleteAll()
        }
        btnRetry.setOnClickListener {
            loadNotifications()
        }

        // Setup RecyclerView
        adapter = NotificationsAdapter(mutableListOf()) { item ->
            handleNotificationClick(item)
        }
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter

        loadNotifications()
    }

    /**
     * Loads notifications from the backend API.
     * Maps ApiNotification → NotificationItem via extension function.
     */
    private fun loadNotifications() {
        showLoadingState()
        if (token.isEmpty()) {
            showErrorState()
            return
        }

        lifecycleScope.launch {
            try {
                val apiNotifications = RetrofitClient.api.getNotifications("Bearer $token")
                val items = apiNotifications.map { it.toNotificationItem() }

                if (items.isEmpty()) {
                    adapter?.clearItems()
                    showEmptyState()
                } else {
                    showNotificationsList()
                    adapter?.updateItems(items)
                    updateActionsVisibility(items)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Erro ao carregar notificações: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showErrorState()
            }
        }
    }

    /**
     * Calls backend to bulk-mark all notifications as read.
     * Updates UI only after backend confirms success.
     */
    private fun markAllAsRead() {
        if (token.isEmpty() || isActionRunning) return

        lifecycleScope.launch {
            try {
                setActionRunning(true)
                val response = RetrofitClient.api.markAllNotificationsRead("Bearer $token")
                val markedCount = (response["marked_count"] as? Number)?.toInt() ?: 0

                // Update UI after backend confirms
                adapter?.markAllAsRead()
                updateActionsVisibility(null)

                val message = if (markedCount > 0) {
                    "$markedCount notificação(ões) marcada(s) como lida(s)"
                } else {
                    "Nenhuma notificação pendente"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Erro ao marcar notificações: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setActionRunning(false)
                updateActionsVisibility(null)
            }
        }
    }

    /**
     * Marks a single notification as read on the backend, then updates UI.
     */
    private fun markSingleAsRead(item: NotificationItem) {
        if (token.isEmpty() || item.isRead) return

        lifecycleScope.launch {
            try {
                val notificationId = item.id.toIntOrNull() ?: return@launch
                RetrofitClient.api.markNotificationRead("Bearer $token", notificationId)
                adapter?.markAsRead(item.id)
                updateActionsVisibility(null)
            } catch (e: Exception) {
                // Silent fail — not critical if individual mark fails
            }
        }
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Apagar notificações")
            .setMessage("Deseja apagar todas as notificações? Esta ação não pode ser desfeita.")
            .setPositiveButton("Apagar") { _, _ -> deleteAllNotifications() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteAllNotifications() {
        if (token.isEmpty() || isActionRunning) return

        lifecycleScope.launch {
            try {
                setActionRunning(true)
                val response = RetrofitClient.api.deleteAllNotifications("Bearer $token")
                val deletedCount = (response["deleted_count"] as? Number)?.toInt() ?: 0
                adapter?.clearItems()
                showEmptyState()
                Toast.makeText(
                    requireContext(),
                    "$deletedCount notificação(ões) apagada(s)",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Erro ao apagar notificações: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setActionRunning(false)
            }
        }
    }

    private fun handleNotificationClick(item: NotificationItem) {
        // Mark as read on click
        markSingleAsRead(item)

        try {
            when (item.type) {
                NotificationType.MESSAGE -> {
                    findNavController().navigate(R.id.chatFragment)
                }
                NotificationType.EVENT -> {
                    findNavController().navigate(R.id.agendaFragment)
                }
                NotificationType.CUSTODY -> {
                    findNavController().navigate(R.id.homeFragment)
                }
                NotificationType.SYSTEM -> {
                    Toast.makeText(requireContext(), item.message, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao navegar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.GONE
        errorState.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        notificationActions.visibility = View.GONE
    }

    private fun showNotificationsList() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
        notificationActions.visibility = View.VISIBLE
    }

    private fun showLoadingState() {
        progressLoading.visibility = View.VISIBLE
        rvNotifications.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.GONE
        notificationActions.visibility = View.GONE
    }

    private fun showErrorState() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        notificationActions.visibility = View.GONE
    }

    private fun updateActionsVisibility(items: List<NotificationItem>?) {
        val currentCount = items?.size ?: adapter?.itemCount ?: 0
        notificationActions.visibility = if (currentCount > 0) View.VISIBLE else View.GONE
        btnMarkAllRead.isEnabled = !isActionRunning &&
                (items?.any { !it.isRead } ?: (adapter?.hasUnreadItems() == true))
        btnDeleteAll.isEnabled = !isActionRunning && currentCount > 0
    }

    private fun setActionRunning(running: Boolean) {
        isActionRunning = running
        btnMarkAllRead.isEnabled = !running && adapter?.hasUnreadItems() == true
        btnDeleteAll.isEnabled = !running && (adapter?.itemCount ?: 0) > 0
    }
}
