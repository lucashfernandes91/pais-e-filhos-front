package com.example.chatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class EventsAdapter(
    private val events: List<Event>,
    private val onDeleteClick: (Event) -> Unit,
    private val onEditClick: (Event) -> Unit = {}
) : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position], onDeleteClick, onEditClick)
    }

    override fun getItemCount() = events.size

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconFrame: FrameLayout = itemView.findViewById(R.id.iconFrame)
        private val tvEventDay: TextView = itemView.findViewById(R.id.tvEventDay)
        private val tvEventMonth: TextView = itemView.findViewById(R.id.tvEventMonth)
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        private val btnAddToCalendar: ImageView = itemView.findViewById(R.id.btnAddToCalendar)

        fun bind(event: Event, onDeleteClick: (Event) -> Unit, onEditClick: (Event) -> Unit) {
            val ctx = itemView.context
            val eventDate = AppDateTime.parseApi(event.event_date)
            val ptBr = Locale.forLanguageTag("pt-BR")
            if (eventDate != null) {
                tvEventDay.text = SimpleDateFormat("dd", ptBr).format(eventDate)
                tvEventMonth.text = SimpleDateFormat("MMM", ptBr)
                    .format(eventDate)
                    .replace(".", "")
                    .uppercase(ptBr)
                iconFrame.contentDescription =
                    SimpleDateFormat("dd 'de' MMMM", ptBr).format(eventDate)
            } else {
                tvEventDay.text = "--"
                tvEventMonth.text = ""
                iconFrame.contentDescription = ctx.getString(R.string.ui_evento)
            }

            tvEventTitle.text = event.title
            tvEventTime.text = formatEventSubtitle(event)

            btnAddToCalendar.setOnClickListener {
                CalendarIntegration.addEventToGoogleCalendar(ctx, event)
            }

            // Tap to edit
            itemView.setOnClickListener { onEditClick(event) }

            // Long press to delete
            itemView.setOnLongClickListener {
                onDeleteClick(event)
                true
            }
        }

        private fun formatEventSubtitle(event: Event): String {
            val date = AppDateTime.parseApi(event.event_date)
            if (date == null) return event.notes.ifEmpty { "Dia inteiro" }
            val ptBr = Locale.forLanguageTag("pt-BR")
            val day = SimpleDateFormat("dd", ptBr).format(date)
            val month = SimpleDateFormat("MMMM", ptBr)
                .format(date)
                .replaceFirstChar { it.titlecase(ptBr) }
            val dayMonth = "$day de $month"
            val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())
            if (AppEventType.fromRaw(event.event_type).isCustody && !event.event_date_end.isNullOrEmpty()) {
                val endDate = AppDateTime.parseApi(event.event_date_end)
                if (endDate != null) {
                    val startStr = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(date)
                    val endStr = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(endDate)
                    return "$startStr → $endStr"
                }
            }
            val cal = Calendar.getInstance().apply { time = date }
            val hasTime = cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0
            return if (hasTime) "$dayMonth · ${timeOnly.format(date)}"
            else if (event.notes.isNotEmpty()) "$dayMonth · ${event.notes}"
            else "$dayMonth · Dia inteiro"
        }
    }
}
