package com.example.chatapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CoParentApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        // Garante o AuthInterceptor (refresh de token) em toda requisição,
        // sem depender de qual Activity abriu primeiro.
        RetrofitClient.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    FCM_CHANNEL_ID,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications from CoParent"
                    enableVibration(true)
                }
            )
        }
    }

    companion object {
        const val FCM_CHANNEL_ID = "chat_notifications"
    }
}
