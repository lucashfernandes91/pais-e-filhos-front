package com.example.chatapp.ui

import com.example.chatapp.ApiNotification
import java.text.SimpleDateFormat
import java.util.*

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val targetId: String? = null
)

enum class NotificationType {
    MESSAGE,
    EVENT,
    CUSTODY,
    SYSTEM
}

/**
 * Maps an ApiNotification from the backend to a NotificationItem for the UI.
 *
 * Notification type is inferred from the title content, following the patterns
 * set in firebase_helpers.py:
 *   - "Mensagem de ..." → MESSAGE
 *   - "Novo compromisso: ..." → EVENT
 *   - Everything else → SYSTEM
 */
fun ApiNotification.toNotificationItem(): NotificationItem {
    val type = when {
        title.startsWith("Mensagem de", ignoreCase = true) -> NotificationType.MESSAGE
        title.startsWith("Novo compromisso", ignoreCase = true) -> NotificationType.EVENT
        title.contains("guarda", ignoreCase = true) ||
            title.contains("convivência", ignoreCase = true) ||
            title.contains("custódia", ignoreCase = true) -> NotificationType.CUSTODY
        else -> NotificationType.SYSTEM
    }

    val timestamp = parseTimestamp(sent_at)

    return NotificationItem(
        id = id.toString(),
        type = type,
        title = title,
        message = body,
        timestamp = timestamp,
        isRead = read_at != null
    )
}

/**
 * Parses ISO datetime string from the backend into epoch millis.
 * Handles multiple common Django datetime formats.
 */
private fun parseTimestamp(dateStr: String): Long {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )

    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            return sdf.parse(dateStr)?.time ?: continue
        } catch (_: Exception) {
            continue
        }
    }

    return System.currentTimeMillis()
}
