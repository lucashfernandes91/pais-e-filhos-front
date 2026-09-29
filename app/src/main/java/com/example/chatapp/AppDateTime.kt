package com.example.chatapp

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single date policy for API values: offsets are honored, while values without
 * an offset are interpreted in the device timezone.
 */
object AppDateTime {
    private val apiPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mmXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd"
    )

    fun parseApi(value: String?): Date? {
        val input = value?.trim().orEmpty()
        if (input.isEmpty()) return null
        val normalized = input.replace(
            Regex("(\\.\\d{3})\\d+(?=Z|[+-]\\d{2}:?\\d{2}$)"),
            "$1"
        )
        return apiPatterns.firstNotNullOfOrNull { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply {
                isLenient = false
                if (!pattern.contains("X")) timeZone = TimeZone.getDefault()
            }
            val position = ParsePosition(0)
            formatter.parse(normalized, position)
                ?.takeIf { position.index == normalized.length }
        }
    }

    fun format(value: String?, pattern: String, locale: Locale = Locale.getDefault()): String? =
        parseApi(value)?.let { date ->
            SimpleDateFormat(pattern, locale).format(date)
        }

    fun dateKey(value: String?): String? = format(value, "yyyy-MM-dd", Locale.ROOT)
}
