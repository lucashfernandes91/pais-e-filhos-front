package com.example.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

class LegalAcceptanceActivity : AppCompatActivity() {
    private lateinit var cbAcceptance: MaterialCheckBox
    private lateinit var btnAccept: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_acceptance)

        cbAcceptance = findViewById(R.id.cbLegalAcceptance)
        btnAccept = findViewById(R.id.btnAcceptLegal)

        findViewById<TextView>(R.id.tvTermsLink).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocuments.TERMS_URL)))
        }
        findViewById<TextView>(R.id.tvPrivacyLink).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocuments.PRIVACY_URL)))
        }

        btnAccept.setOnClickListener { submitAcceptance() }
    }

    private fun submitAcceptance() {
        if (!cbAcceptance.isChecked) {
            cbAcceptance.error = getString(R.string.register_legal_acceptance_required)
            return
        }

        val token = PrefsHelper.getAuthToken(this)
        if (token.isEmpty()) {
            finish()
            return
        }

        btnAccept.isEnabled = false
        lifecycleScope.launch {
            try {
                RetrofitClient.api.acceptLegalDocuments(
                    "Bearer $token",
                    mapOf(
                        "terms_version" to LegalDocuments.TERMS_VERSION,
                        "privacy_version" to LegalDocuments.PRIVACY_VERSION,
                        "accept_terms" to "true",
                        "accept_privacy" to "true"
                    )
                )
                startActivity(Intent(this@LegalAcceptanceActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (_: Exception) {
                btnAccept.isEnabled = true
                Toast.makeText(
                    this@LegalAcceptanceActivity,
                    getString(R.string.legal_acceptance_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
