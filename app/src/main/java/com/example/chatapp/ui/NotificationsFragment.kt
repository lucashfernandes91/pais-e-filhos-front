package com.example.chatapp.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyState: View
    private lateinit var btnMarkAllRead: TextView

    private var adapter: NotificationsAdapter? = null
    private var token: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvNotifications = view.findViewById(R.id.rvNotifications)
        emptyState = view.findViewById(R.id.emptyState)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)

        token = PrefsHelper.getAuthToken(requireContext())

        // Voltar
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Marcar todas como lidas
        btnMarkAllRead.setOnClickListener {
            markAllAsRead()
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
        if (token.isEmpty()) {
            showEmptyState()
            return
        }

        lifecycleScope.launch {
            try {
                val apiNotifications = RetrofitClient.api.getNotifications("Bearer $token")
                val items = apiNotifications.map { it.toNotificationItem() }

                if (items.isEmpty()) {
                    showEmptyState()
                } else {
                    showNotificationsList()
                    adapter?.updateItems(items)
                    updateMarkAllButtonVisibility(items)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Erro ao carregar notificações: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showEmptyState()
            }
        }
    }

    /**
     * Calls backend to bulk-mark all notifications as read.
     * Updates UI only after backend confirms success.
     */
    private fun markAllAsRead() {
        if (token.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.markAllNotificationsRead("Bearer $token")
                val markedCount = (response["marked_count"] as? Number)?.toInt() ?: 0

                // Update UI after backend confirms
                adapter?.markAllAsRead()
                btnMarkAllRead.visibility = View.GONE

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
                updateMarkAllButtonVisibility(null)
            } catch (e: Exception) {
                // Silent fail — not critical if individual mark fails
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
        rvNotifications.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        btnMarkAllRead.visibility = View.GONE
    }

    private fun showNotificationsList() {
        rvNotifications.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    /**
     * Shows/hides the "Marcar todas como lidas" button based on unread count.
     */
    private fun updateMarkAllButtonVisibility(items: List<NotificationItem>?) {
        val hasUnread: Boolean = if (items != null) {
            items.any { !it.isRead }
        } else {
            (adapter?.itemCount ?: 0) > 0
        }

        btnMarkAllRead.visibility = if (hasUnread) View.VISIBLE else View.GONE
    }
}
