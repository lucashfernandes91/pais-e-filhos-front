package com.example.chatapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
        Log.d(TAG, "New FCM token: $token")
        sendTokenToBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "Mensagem"
        val body = remoteMessage.notification?.body ?: ""

        Log.d(TAG, "Message received - Title: $title, Body: $body")

        // Check user preferences before showing notification
        if (!shouldShowNotification()) {
            Log.d(TAG, "Notification suppressed by user preferences")
            return
        }

        showNotification(title, body)
    }

    /**
     * Checks user notification preferences:
     * - Push notifications enabled/disabled
     * - Night mode (suppress between 22h and 7h)
     */
    private fun shouldShowNotification(): Boolean {
        if (!PrefsHelper.isPushEnabled(this)) return false

        if (PrefsHelper.isNightModeEnabled(this)) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 7) return false
        }

        return true
    }

    private fun sendTokenToBackend(token: String) {
        Log.d(TAG, "Sending token to backend: $token")

        // Salva token localmente via PrefsHelper
        PrefsHelper.saveFcmToken(this, token)

        // Pega auth token
        val authToken = PrefsHelper.getAuthToken(this)
        if (authToken.isEmpty()) {
            Log.w(TAG, "No auth token found, skipping backend registration")
            return
        }

        // Envia ao backend com scope cancelável
        serviceScope.launch {
            try {
                val response = RetrofitClient.api.registerDeviceToken(
                    "Bearer $authToken",
                    mapOf("token" to token)
                )
                Log.d(TAG, "Token registered: $response")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering token: ${e.message}")
            }
        }
    }

    private fun showNotification(title: String, body: String) {
        val notificationId = System.currentTimeMillis().toInt()
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
            notificationId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notification shown: $notificationId")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChatMessagingService"
        private const val CHANNEL_ID = "chat_notifications"
    }
}
