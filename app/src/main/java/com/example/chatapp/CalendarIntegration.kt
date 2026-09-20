package com.example.chatapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalendarIntegration {

    /**
     * Adiciona um evento ao Google Calendar.
     */
    fun addEventToGoogleCalendar(context: Context, event: Event) {
        try {
            val (startTimeMillis, endTimeMillis) = parseEventDateTime(event.event_date)

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.Events.DESCRIPTION, buildDescription(context, event))
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                putExtra(CalendarContract.Events.ALL_DAY, isAllDayEvent(event))
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                putExtra(CalendarContract.Reminders.MINUTES, 15)
                putExtra(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }

            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                R.string.calendar_app_missing,
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.calendar_add_error, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun parseEventDateTime(eventDate: String): Pair<Long, Long> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val dateFormatAlt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val date = try {
            dateFormat.parse(eventDate)
        } catch (_: Exception) {
            dateFormatAlt.parse(eventDate)
        } ?: Date()

        val calendar = Calendar.getInstance().apply {
            time = date
        }

        val startTimeMillis = calendar.timeInMillis

        val endCalendar = calendar.clone() as Calendar
        if (eventDate.contains("T")) {
            endCalendar.add(Calendar.HOUR, 1)
        } else {
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
        }

        return Pair(startTimeMillis, endCalendar.timeInMillis)
    }

    private fun isAllDayEvent(event: Event): Boolean = !event.event_date.contains("T")

    private fun buildDescription(context: Context, event: Event): String {
        val lines = mutableListOf<String>()
        lines += context.getString(
            R.string.calendar_description_type,
            context.getString(AppEventType.fromRaw(event.event_type).labelRes)
        )

        if (event.notes.isNotEmpty()) {
            lines += ""
            lines += context.getString(R.string.calendar_description_notes, event.notes)
        }

        lines += ""
        lines += "---"
        lines += context.getString(R.string.calendar_description_created_by, event.created_by_name)
        lines += context.getString(R.string.calendar_description_source, context.getString(R.string.app_name))

        return lines.joinToString("\n")
    }
}
