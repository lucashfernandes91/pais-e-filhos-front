package com.example.chatapp.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.chatapp.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Notification settings screen.
 *
 * Persists user preferences in SharedPreferences under "coparent" namespace.
 * Keys: "pref_push_enabled", "pref_email_backup", "pref_night_mode".
 *
 * These preferences will be read by ChatMessagingService to decide
 * whether to show push notifications and by future email backup logic.
 */
class NotificationSettingsFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "coparent"
        private const val KEY_PUSH_ENABLED = "pref_push_enabled"
        private const val KEY_EMAIL_BACKUP = "pref_email_backup"
        private const val KEY_NIGHT_MODE = "pref_night_mode"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notification_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val switchPush = view.findViewById<SwitchMaterial>(R.id.switchPush)
        val switchEmail = view.findViewById<SwitchMaterial>(R.id.switchEmailBackup)
        val switchNight = view.findViewById<SwitchMaterial>(R.id.switchNightMode)

        // Load saved state (defaults: push=true, email=true, night=false)
        switchPush.isChecked = prefs.getBoolean(KEY_PUSH_ENABLED, true)
        switchEmail.isChecked = prefs.getBoolean(KEY_EMAIL_BACKUP, true)
        switchNight.isChecked = prefs.getBoolean(KEY_NIGHT_MODE, false)

        // Back button
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Save on change — immediate persistence
        switchPush.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_PUSH_ENABLED, isChecked).apply()
            val msg = if (isChecked) "Notificações push ativadas" else "Notificações push desativadas"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_EMAIL_BACKUP, isChecked).apply()
            val msg = if (isChecked) "Email de backup ativado" else "Email de backup desativado"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        switchNight.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NIGHT_MODE, isChecked).apply()
            val msg = if (isChecked) "Modo silencioso noturno ativado" else "Modo silencioso noturno desativado"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}
