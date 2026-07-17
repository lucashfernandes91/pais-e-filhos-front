package com.example.chatapp

import android.content.Context

/**
 * Conversão dp→px única do app (C2) — substitui as cópias locais de `dp()`
 * espalhadas por Home/Profile/Agenda/Onboarding.
 */
fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
