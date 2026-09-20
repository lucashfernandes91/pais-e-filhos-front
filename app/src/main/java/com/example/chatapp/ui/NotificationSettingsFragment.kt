package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Notification settings screen.
 *
 * Toda leitura/escrita de preferências é delegada ao [PrefsHelper]
 * para garantir uma única fonte de verdade — sem isso, fragments e
 * services divergiam na chave usada (push_notifications vs pref_push_enabled)
 * e o toggle do usuário não chegava ao ChatMessagingService.
 */
class NotificationSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notification_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val switchPush = view.findViewById<SwitchMaterial>(R.id.switchPush)
        val switchEmail = view.findViewById<SwitchMaterial>(R.id.switchEmailBackup)
        val switchNight = view.findViewById<SwitchMaterial>(R.id.switchNightMode)

        switchPush.isChecked = PrefsHelper.isPushEnabled(ctx)
        switchEmail.isChecked = PrefsHelper.isEmailBackupEnabled(ctx)
        switchNight.isChecked = PrefsHelper.isNightModeEnabled(ctx)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        switchPush.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setPushEnabled(ctx, isChecked)
            toast(if (isChecked) R.string.settings_push_enabled else R.string.settings_push_disabled)
        }

        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setEmailBackupEnabled(ctx, isChecked)
            toast(if (isChecked) R.string.settings_email_enabled else R.string.settings_email_disabled)
        }

        switchNight.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setNightModeEnabled(ctx, isChecked)
            toast(if (isChecked) R.string.settings_night_enabled else R.string.settings_night_disabled)
        }
    }

    private fun toast(stringRes: Int) {
        Toast.makeText(requireContext(), getString(stringRes), Toast.LENGTH_SHORT).show()
    }
}
