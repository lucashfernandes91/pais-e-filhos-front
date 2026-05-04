package com.example.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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

        // Find views
        switchPushNotifications = view.findViewById(R.id.switchPushNotifications)
        switchEmailNotifications = view.findViewById(R.id.switchEmailNotifications)

        // Load saved settings
        loadSettings()

        // Outro responsável - verificar se existe
        setupOtherParent(view)

        // Set listeners
        switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setPushEnabled(requireContext(), isChecked)
            Toast.makeText(
                context,
                if (isChecked) "Notificações push ativadas" else "Notificações push desativadas",
                Toast.LENGTH_SHORT
            ).show()
        }

        switchEmailNotifications.setOnCheckedChangeListener { _, isChecked ->
            PrefsHelper.setEmailEnabled(requireContext(), isChecked)
            Toast.makeText(
                context,
                if (isChecked) "Email ativado" else "Email desativado",
                Toast.LENGTH_SHORT
            ).show()
        }

        view.findViewById<View>(R.id.btnExportData)?.setOnClickListener { exportData() }
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
                Toast.makeText(requireContext(), "Convite em breve!", Toast.LENGTH_SHORT).show()
            }
        } else {
            cardOtherParent.visibility = View.VISIBLE
            cardInviteParent.visibility = View.GONE

            view.findViewById<android.widget.TextView>(R.id.tvOtherParentName)?.text = otherParentName
            view.findViewById<android.widget.TextView>(R.id.tvOtherParentInitial)?.text =
                otherParentName.first().uppercase()
        }
    }

    private fun exportData() {
        val conversationId = PrefsHelper.getConversationId(requireContext())
        val authToken = PrefsHelper.getAuthToken(requireContext())

        if (authToken.isEmpty()) {
            Toast.makeText(requireContext(), "Faça login para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Iniciando download do PDF...", Toast.LENGTH_SHORT).show()
        PdfDownloadHelper.downloadConversationPdf(
            requireContext(),
            authToken,
            conversationId,
            viewLifecycleOwner.lifecycleScope
        )
    }

    private fun openPrivacyPolicy() {
        val url = "https://coparent.app/privacy"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun logout() {
        PrefsHelper.clearAll(requireContext())

        Toast.makeText(requireContext(), "Desconectado", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
