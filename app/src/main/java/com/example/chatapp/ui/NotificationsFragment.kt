package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyState: View
    private lateinit var errorState: View
    private lateinit var btnMarkAllRead: View
    private lateinit var btnDeleteAll: ImageView
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
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        btnDeleteAll = view.findViewById(R.id.btnDeleteAllNotifications)
        btnRetry = view.findViewById(R.id.btnRetryNotifications)
        progressLoading = view.findViewById(R.id.progressLoading)

        token = PrefsHelper.getAuthToken(requireContext())

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        btnMarkAllRead.setOnClickListener { markAllAsRead() }
        loadDeleteAllIcon()
        btnDeleteAll.setOnClickListener {
            playDeleteAllAnimation()
            confirmDeleteAll()
        }
        btnRetry.setOnClickListener { loadNotifications() }

        adapter = NotificationsAdapter(mutableListOf()) { item ->
            handleNotificationClick(item)
        }
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter

        loadNotifications()
    }

    /**
     * Loads notifications from the backend API.
     * Maps ApiNotification -> NotificationItem via extension function.
     */
    private fun loadNotifications() {
        showLoadingState()
        token = PrefsHelper.getAuthToken(requireContext())
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
                    getString(R.string.notifications_load_error, e.message.orEmpty()),
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
        token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty() || isActionRunning) return

        lifecycleScope.launch {
            try {
                setActionRunning(true)
                val response = RetrofitClient.api.markAllNotificationsRead("Bearer $token")
                val markedCount = (response["marked_count"] as? Number)?.toInt() ?: 0

                adapter?.markAllAsRead()
                updateActionsVisibility(null)

                val message = if (markedCount > 0) {
                    resources.getQuantityString(
                        R.plurals.notifications_marked_count,
                        markedCount,
                        markedCount
                    )
                } else {
                    getString(R.string.notifications_none_pending)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_mark_error, e.message.orEmpty()),
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
        token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty() || item.isRead) return

        lifecycleScope.launch {
            try {
                val notificationId = item.id.toIntOrNull() ?: return@launch
                RetrofitClient.api.markNotificationRead("Bearer $token", notificationId)
                adapter?.markAsRead(item.id)
                updateActionsVisibility(null)
            } catch (_: Exception) {
                // Silent fail; not critical if individual mark fails.
            }
        }
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notifications_delete_title)
            .setMessage(R.string.notifications_delete_message)
            .setPositiveButton(R.string.notifications_delete_confirm) { _, _ -> deleteAllNotifications() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun deleteAllNotifications() {
        token = PrefsHelper.getAuthToken(requireContext())
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
                    resources.getQuantityString(
                        R.plurals.notifications_deleted_count,
                        deletedCount,
                        deletedCount
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_delete_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setActionRunning(false)
            }
        }
    }

    private fun handleNotificationClick(item: NotificationItem) {
        markSingleAsRead(item)

        try {
            when (item.type) {
                NotificationType.MESSAGE -> findNavController().navigate(R.id.chatFragment)
                NotificationType.EVENT -> findNavController().navigate(R.id.agendaFragment)
                NotificationType.CUSTODY -> findNavController().navigate(R.id.homeFragment)
                NotificationType.SYSTEM -> {
                    Toast.makeText(requireContext(), item.message, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.notifications_navigation_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.GONE
        errorState.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        setActionEnabled(btnMarkAllRead, false)
        setActionEnabled(btnDeleteAll, false)
    }

    private fun showNotificationsList() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.VISIBLE
        errorState.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun showLoadingState() {
        progressLoading.visibility = View.VISIBLE
        rvNotifications.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.GONE
        setActionEnabled(btnMarkAllRead, false)
        setActionEnabled(btnDeleteAll, false)
    }

    private fun showErrorState() {
        progressLoading.visibility = View.GONE
        rvNotifications.visibility = View.GONE
        emptyState.visibility = View.GONE
        errorState.visibility = View.VISIBLE
        setActionEnabled(btnMarkAllRead, false)
        setActionEnabled(btnDeleteAll, false)
    }

    private fun updateActionsVisibility(items: List<NotificationItem>?) {
        val currentCount = items?.size ?: adapter?.itemCount ?: 0
        val hasUnread = items?.any { !it.isRead } ?: (adapter?.hasUnreadItems() == true)
        setActionEnabled(btnMarkAllRead, !isActionRunning && hasUnread)
        setActionEnabled(btnDeleteAll, !isActionRunning && currentCount > 0)
    }

    private fun setActionRunning(running: Boolean) {
        isActionRunning = running
        setActionEnabled(btnMarkAllRead, !running && adapter?.hasUnreadItems() == true)
        setActionEnabled(btnDeleteAll, !running && (adapter?.itemCount ?: 0) > 0)
    }

    /** Ícones do top bar não têm estado "disabled" nativo como o MaterialButton; simulamos com alpha. */
    private fun setActionEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
    }

    /** Carrega o gif da lixeira parado no primeiro frame; só anima quando o usuário toca. */
    private fun loadDeleteAllIcon() {
        Glide.with(this)
            .asGif()
            .load(R.raw.trash_delete)
            .listener(object : RequestListener<GifDrawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<GifDrawable>,
                    isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: GifDrawable,
                    model: Any,
                    target: Target<GifDrawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    resource.stop()
                    // O gif já tem cor própria; remove o tint neutro usado no fallback estático.
                    btnDeleteAll.imageTintList = null
                    btnDeleteAll.setImageDrawable(resource)
                    // Retorna true: impede o autoplay padrão do Target do Glide, que rodaria
                    // start() por cima do stop() acima assim que o resource fosse entregue.
                    return true
                }
            })
            .into(btnDeleteAll)
    }

    private fun playDeleteAllAnimation() {
        val gif = btnDeleteAll.drawable as? GifDrawable ?: return
        gif.setLoopCount(1)
        gif.stop()
        gif.start()
    }
}
