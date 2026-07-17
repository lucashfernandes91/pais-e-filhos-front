package com.example.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var switchPushNotifications: SwitchMaterial
    private lateinit var switchEmailNotifications: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchPushNotifications = view.findViewById(R.id.switchPushNotifications)
        switchEmailNotifications = view.findViewById(R.id.switchEmailNotifications)

        loadSettings()
        setupOtherParent(view)

        switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setPushEnabled(requireContext(), isChecked)
            Toast.makeText(
                context,
                getString(
                    if (isChecked) R.string.settings_push_enabled else R.string.settings_push_disabled
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

        switchEmailNotifications.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setEmailEnabled(requireContext(), isChecked)
            Toast.makeText(
                context,
                getString(
                    if (isChecked) R.string.settings_email_enabled else R.string.settings_email_disabled
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

        view.findViewById<View>(R.id.btnPrivacy)?.setOnClickListener { openPrivacyPolicy() }
        view.findViewById<View>(R.id.btnLogout)?.setOnClickListener { logout() }
    }

    private fun loadSettings() {
        switchPushNotifications.isChecked = PrefsHelper.isPushEnabled(requireContext())
        switchEmailNotifications.isChecked = PrefsHelper.isEmailEnabled(requireContext())
    }

    private fun setupOtherParent(view: View) {
        val cardOtherParent = view.findViewById<View>(R.id.cardOtherParent)
        val cardInviteParent = view.findViewById<View>(R.id.cardInviteParent)

        val otherParentName = PrefsHelper.getOtherParentName(requireContext())

        if (otherParentName.isNullOrEmpty()) {
            cardOtherParent.visibility = View.GONE
            cardInviteParent.visibility = View.VISIBLE

            cardInviteParent.setOnClickListener {
                InviteHelper.shareInvite(this)
            }
        } else {
            cardOtherParent.visibility = View.VISIBLE
            cardInviteParent.visibility = View.GONE

            view.findViewById<android.widget.TextView>(R.id.tvOtherParentName)?.text = otherParentName
            view.findViewById<android.widget.TextView>(R.id.tvOtherParentInitial)?.text =
                otherParentName.first().uppercase()
        }
    }

    private fun openPrivacyPolicy() {
        val url = "https://coparent.app/privacy"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun logout() {
        LogoutHelper.notifyServerLogout(requireContext())
        PrefsHelper.clearAll(requireContext())

        Toast.makeText(requireContext(), getString(R.string.settings_logged_out), Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
