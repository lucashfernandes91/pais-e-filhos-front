package com.example.chatapp

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Paleta de cores personalizáveis para os usuários na legenda do calendário.
 *
 * Cada entrada define:
 *  - [id]           chave salva em SharedPreferences
 *  - [dotColorHex]  cor do ponto na legenda e do fundo no calendário (dia claro)
 *  - [bgColorHex]   fundo pastel dos dias no calendário (light mode)
 *  - [bgColorDarkHex] fundo para dark mode
 */
enum class CalendarColor(
    val id: String,
    val dotColorHex: String,
    val bgColorHex: String,
    val bgColorDarkHex: String
) {
    BLUE(
        id = "BLUE",
        dotColorHex = "#3B82F6",
        bgColorHex = "#BFDBFE",
        bgColorDarkHex = "#1E3A5F"
    ),
    PINK(
        id = "PINK",
        dotColorHex = "#EC4899",
        bgColorHex = "#FBCFE8",
        bgColorDarkHex = "#5C1A3E"
    ),
    PURPLE(
        id = "PURPLE",
        dotColorHex = "#8B5CF6",
        bgColorHex = "#DDD6FE",
        bgColorDarkHex = "#3B1E5F"
    ),
    GREEN(
        id = "GREEN",
        dotColorHex = "#10B981",
        bgColorHex = "#A7F3D0",
        bgColorDarkHex = "#134E4A"
    ),
    CYAN(
        id = "CYAN",
        dotColorHex = "#06B6D4",
        bgColorHex = "#A5F3FC",
        bgColorDarkHex = "#164E63"
    );

    /** Retorna [dotColorHex] como [ColorInt]. */
    @ColorInt
    fun dotColor(): Int = Color.parseColor(dotColorHex)

    /** Retorna a cor de fundo correta para o modo atual (light/dark). */
    @ColorInt
    fun bgColor(context: Context): Int {
        val isNight = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        return Color.parseColor(if (isNight) bgColorDarkHex else bgColorHex)
    }

    fun displayName(context: Context): String = context.getString(
        when (this) {
            BLUE -> R.string.calendar_color_blue
            PINK -> R.string.calendar_color_pink
            PURPLE -> R.string.calendar_color_purple
            GREEN -> R.string.calendar_color_green
            CYAN -> R.string.calendar_color_cyan
        }
    )

    companion object {
        val DEFAULT_USER = BLUE
        val DEFAULT_OTHER = PINK

        /** Converte uma string de ID salva em prefs para o enum, com fallback. */
        fun fromId(id: String?, fallback: CalendarColor = DEFAULT_USER): CalendarColor =
            values().firstOrNull { it.id == id } ?: fallback
    }
}
