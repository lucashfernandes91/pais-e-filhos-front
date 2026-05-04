package com.example.chatapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

object CalendarIntegration {
    
    /**
     * Adiciona um evento ao Google Calendar
     * @param context Contexto da aplicação
     * @param event Evento a ser adicionado
     */
    fun addEventToGoogleCalendar(context: Context, event: Event) {
        try {
            val (startTimeMillis, endTimeMillis) = parseEventDateTime(event.event_date)
            
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.Events.DESCRIPTION, buildDescription(event))
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                putExtra(CalendarContract.Events.ALL_DAY, isAllDayEvent(event))
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                
                // Adicionar lembrete 15 minutos antes
                putExtra(CalendarContract.Reminders.MINUTES, 15)
                putExtra(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            
            context.startActivity(intent)
            
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Google Calendar não está instalado",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Erro ao adicionar evento: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Converte a data do evento em milissegundos
     */
    private fun parseEventDateTime(eventDate: String): Pair<Long, Long> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val dateFormatAlt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val date = try {
            dateFormat.parse(eventDate)
        } catch (e: Exception) {
            dateFormatAlt.parse(eventDate)
        } ?: Date()
        
        val calendar = Calendar.getInstance().apply {
            time = date
        }
        
        val startTimeMillis = calendar.timeInMillis
        
        // Se for evento de dia inteiro, termina às 23:59 do mesmo dia
        // Senão, adiciona 1 hora
        val endCalendar = calendar.clone() as Calendar
        if (eventDate.contains("T")) {
            endCalendar.add(Calendar.HOUR, 1)
        } else {
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
        }
        
        return Pair(startTimeMillis, endCalendar.timeInMillis)
    }
    
    /**
     * Verifica se é evento de dia inteiro
     */
    private fun isAllDayEvent(event: Event): Boolean {
        return !event.event_date.contains("T")
    }
    
    /**
     * Constrói a descrição do evento
     */
    private fun buildDescription(event: Event): String {
        val description = StringBuilder()
        
        description.append("Tipo: ${formatEventType(event.event_type)}\n")
        
        if (event.notes.isNotEmpty()) {
            description.append("\nNotas:\n${event.notes}")
        }
        
        description.append("\n\n---\n")
        description.append("Criado por: ${event.created_by_name}\n")
        description.append("Via ChatApp")
        
        return description.toString()
    }
    
    /**
     * Formata o tipo de evento para exibição
     */
    private fun formatEventType(type: String): String {
        return when (type.uppercase()) {
            "CUSTODY" -> "Custódia"
            "SCHOOL" -> "Escola"
            "MEDICAL" -> "Médico"
            "GENERAL" -> "Geral"
            else -> type
        }
    }
}
