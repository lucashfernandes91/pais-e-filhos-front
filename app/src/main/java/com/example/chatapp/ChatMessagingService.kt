package com.example.chatapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatMessagingService : FirebaseMessagingService() {

    /**
     * Scope próprio com SupervisorJob: falha em uma coroutine
     * não cancela as demais. Cancelado no onDestroy().
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppTelemetry.info("fcm_token_received")
        sendTokenToBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check user preferences before showing notification
        if (!shouldShowNotification()) {
            AppTelemetry.debug(TAG, "Notification suppressed by user preferences")
            return
        }

        val data = remoteMessage.data
        val notificationType = data["notification_type"]
        if (notificationType == MESSAGE_NOTIFICATION_TYPE) {
            showMessageNotification(data["message_id"])
            return
        }

        val title = data["title"] ?: remoteMessage.notification?.title ?: "CoParent"
        val body = data["body"] ?: remoteMessage.notification?.body.orEmpty()
        showNotification(title, body, notificationId = data["notification_id"]?.hashCode())
    }

    /**
     * Checks user notification preferences:
     * - Push notifications enabled/disabled
     * - Night mode (suppress between 22h and 7h)
     */
    private fun shouldShowNotification(): Boolean {
        if (!PrefsHelper.isPushEnabled(this)) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        if (PrefsHelper.isNightModeEnabled(this)) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 7) return false
        }

        return true
    }

    private fun sendTokenToBackend(token: String) {
        val correlationId = AppTelemetry.newCorrelationId()
        AppTelemetry.info("fcm_token_registration_started", correlationId)

        // Salva token localmente via PrefsHelper
        PrefsHelper.saveFcmToken(this, token)

        // Pega auth token
        val authToken = PrefsHelper.getAuthToken(this)
        if (authToken.isEmpty()) {
            AppTelemetry.warning("fcm_token_registration_skipped", correlationId)
            return
        }

        // Envia ao backend com scope cancelável
        serviceScope.launch {
            try {
                val response = RetrofitClient.api.registerDeviceToken(
                    "Bearer $authToken",
                    mapOf("token" to token)
                )
                AppTelemetry.info("fcm_token_registration_succeeded", correlationId)
            } catch (e: Exception) {
                AppTelemetry.error("fcm_token_registration_failed", correlationId, e)
            }
        }
    }

    private fun showMessageNotification(messageId: String?) {
        showNotification(
            title = MESSAGE_NOTIFICATION_TITLE,
            body = MESSAGE_NOTIFICATION_BODY,
            lockScreenTitle = LOCK_SCREEN_NOTIFICATION_TITLE,
            notificationId = messageId?.hashCode()
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        lockScreenTitle: String? = null,
        notificationId: Int? = null
    ) {
        if (!shouldShowNotification()) return
        val stableNotificationId = notificationId ?: "$title:$body".hashCode()
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from CoParent app"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep link para a tela de chat
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("coparent://chat"),
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            stableNotificationId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        if (lockScreenTitle != null) {
            val lockScreenNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(lockScreenTitle)
                .setAutoCancel(true)
                .build()
            notificationBuilder
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenNotification)
        } else {
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        val notification = notificationBuilder.build()

        notificationManager.notify(stableNotificationId, notification)
        AppTelemetry.info("fcm_notification_shown")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChatMessagingService"
        private const val CHANNEL_ID = CoParentApplication.FCM_CHANNEL_ID
        private const val MESSAGE_NOTIFICATION_TYPE = "message"
        private const val MESSAGE_NOTIFICATION_TITLE = "Nova mensagem"
        private const val MESSAGE_NOTIFICATION_BODY = "Você recebeu uma nova mensagem."
        private const val LOCK_SCREEN_NOTIFICATION_TITLE = "Nova notificação"
    }
}
