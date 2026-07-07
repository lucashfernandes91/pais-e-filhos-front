package com.example.chatapp

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class AppEventType(
    val rawValue: String,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int
) {
    SCHOOL("SCHOOL", R.drawable.ic_school, R.string.event_type_school),
    MEDICAL("MEDICAL", R.drawable.ic_health, R.string.event_type_medical),
    CUSTODY("CUSTODY", R.drawable.ic_custody, R.string.event_type_custody),
    OTHER("OTHER", R.drawable.ic_other, R.string.event_type_other);

    val isCustody: Boolean
        get() = this == CUSTODY

    companion object {
        fun fromRaw(rawValue: String?): AppEventType {
            return entries.firstOrNull { it.rawValue.equals(rawValue, ignoreCase = true) } ?: OTHER
        }
    }
}
