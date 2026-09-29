package com.example.chatapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventReminderScheduler {

    private const val ACTION_EVENT_REMINDER = "com.example.chatapp.EVENT_REMINDER"
    private const val CHANNEL_ID = "event_reminders"
    private const val EXTRA_EVENT_ID = "event_id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_EVENT_DATE = "event_date"
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun sync(context: Context, events: List<Event>) {
        if (!PrefsHelper.isEventRemindersEnabled(context)) {
            clearAll(context)
            return
        }
        val eventIds = events.map { it.id }.toSet()
        PrefsHelper.getEventReminders(context).keys
            .filterNot { it in eventIds }
            .forEach { cancel(context, it) }
        events.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, event: Event) {
        val triggerAt = parseEventDate(event.event_date)?.time?.minus(DAY_MILLIS) ?: return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val intent = reminderIntent(appContext, event.id, event.title, event.event_date)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            event.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (triggerAt > System.currentTimeMillis()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
        PrefsHelper.saveEventReminder(appContext, event)
    }

    fun cancel(context: Context, eventId: Int) {
        val appContext = context.applicationContext
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            eventId,
            reminderIntent(appContext, eventId, "", ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
        PrefsHelper.removeEventReminder(appContext, eventId)
    }

    fun clearAll(context: Context) {
        PrefsHelper.getEventReminders(context).keys.forEach { cancel(context, it) }
    }

    fun rescheduleStored(context: Context) {
        if (!PrefsHelper.isEventRemindersEnabled(context)) return
        PrefsHelper.getEventReminders(context).values.forEach { schedule(context, it) }
    }

    private fun reminderIntent(context: Context, eventId: Int, title: String, eventDate: String) =
        Intent(context, EventReminderReceiver::class.java).apply {
            action = ACTION_EVENT_REMINDER
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_EVENT_DATE, eventDate)
        }

    private fun parseEventDate(value: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd"
        )
        formats.forEach { format ->
            runCatching {
                return SimpleDateFormat(format, Locale.getDefault()).parse(value)
            }
        }
        return null
    }

    internal fun showNotification(context: Context, eventId: Int, title: String, eventDate: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Lembretes de eventos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Lembretes da agenda 24 horas antes" }
            )
        }

        val openAgenda = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("coparent://agenda")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId,
            openAgenda,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dateText = eventDate.substringBefore('T').takeIf { it.isNotBlank() }.orEmpty()
        manager.notify(
            eventId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle("Lembrete de evento")
                .setContentText("$title${if (dateText.isNotBlank()) " • $dateText" else ""}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Você tem o evento \"$title\" agendado para amanhã."))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.chatapp.EVENT_REMINDER" ||
            !PrefsHelper.isEventRemindersEnabled(context)
        ) return

        val eventId = intent.getIntExtra("event_id", 0)
        val title = intent.getStringExtra("title").orEmpty()
        val eventDate = intent.getStringExtra("event_date").orEmpty()
        if (eventId > 0 && title.isNotBlank()) {
            EventReminderScheduler.showNotification(context, eventId, title, eventDate)
            PrefsHelper.markEventReminderShown(context, eventId)
        }
    }
}

class EventReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            EventReminderScheduler.rescheduleStored(context)
        }
    }
}
