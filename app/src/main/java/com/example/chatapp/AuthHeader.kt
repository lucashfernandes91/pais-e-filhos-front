package com.example.chatapp

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.setupAuthHeader(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    showBack: Boolean,
    onBack: (() -> Unit)? = null,
) {
    findViewById<TextView>(R.id.tvAuthTitle).setText(titleRes)
    findViewById<TextView>(R.id.tvAuthDescription).setText(descriptionRes)
    findViewById<ImageButton>(R.id.btnBack).apply {
        visibility = if (showBack) View.VISIBLE else View.GONE
        setOnClickListener(if (showBack) View.OnClickListener {
            onBack?.invoke() ?: onBackPressedDispatcher.onBackPressed()
        } else null)
    }
}
