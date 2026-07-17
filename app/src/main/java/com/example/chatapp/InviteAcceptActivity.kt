package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Aceita o convite do co-responsável.
 *
 * Aberta por deep link (https://coparent.app/convite/CODIGO ou
 * coparent://convite/CODIGO) com o código já preenchido, ou manualmente
 * pelo Perfil para digitar o código recebido por texto.
 * Sem sessão ativa, guarda o código e envia para o login; MainActivity
 * reabre esta tela depois que o usuário entrar.
 */
class InviteAcceptActivity : AppCompatActivity() {

    private lateinit var tilInviteCode: TextInputLayout
    private lateinit var etInviteCode: TextInputEditText
    private lateinit var tvInviteError: TextView
    private lateinit var btnAcceptInvite: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = extractCode()

        if (PrefsHelper.getAuthToken(this).isEmpty()) {
            if (!code.isNullOrBlank()) {
                PrefsHelper.savePendingInviteCode(this, code)
            }
            Toast.makeText(this, R.string.invite_login_first, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        setContentView(R.layout.activity_invite_accept)
        RetrofitClient.init(this)

        tilInviteCode = findViewById(R.id.tilInviteCode)
        etInviteCode = findViewById(R.id.etInviteCode)
        tvInviteError = findViewById(R.id.tvInviteError)
        btnAcceptInvite = findViewById(R.id.btnAcceptInvite)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        if (!code.isNullOrBlank()) {
            etInviteCode.setText(code)
        }

        btnAcceptInvite.setOnClickListener { attemptAccept() }
        etInviteCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptAccept()
                true
            } else false
        }
    }

    /** Código vem do deep link (último segmento do path), do extra ou pendente. */
    private fun extractCode(): String? {
        intent?.data?.lastPathSegment?.let { segment ->
            if (segment.isNotBlank() && segment != "convite") return segment
        }
        intent?.getStringExtra(EXTRA_CODE)?.let { extra ->
            if (extra.isNotBlank()) return extra
        }
        return PrefsHelper.getPendingInviteCode(this)
    }

    private fun attemptAccept() {
        hideError()
        val code = etInviteCode.text.toString().trim().uppercase()

        if (code.isEmpty()) {
            tilInviteCode.error = getString(R.string.invite_code_required)
            return
        }
        tilInviteCode.error = null

        btnAcceptInvite.isEnabled = false
        btnAcceptInvite.setText(R.string.invite_accepting)

        val token = PrefsHelper.getAuthToken(this)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.acceptInvite(
                    "Bearer $token", mapOf("code" to code)
                )

                PrefsHelper.clearPendingInviteCode(this@InviteAcceptActivity)

                val conversationId = (response["conversation_id"] as? Number)?.toInt()
                if (conversationId != null) {
                    PrefsHelper.saveConversationId(this@InviteAcceptActivity, conversationId)
                }
                @Suppress("UNCHECKED_CAST")
                val participants = response["participants"] as? List<Map<String, Any>>
                val otherParent = participants
                    ?.firstOrNull { (it["is_me"] as? Boolean) == false }
                    ?.get("username") as? String
                if (!otherParent.isNullOrBlank()) {
                    PrefsHelper.saveOtherParentName(this@InviteAcceptActivity, otherParent)
                }

                Toast.makeText(
                    this@InviteAcceptActivity,
                    R.string.invite_accepted_success,
                    Toast.LENGTH_LONG
                ).show()

                startActivity(
                    Intent(this@InviteAcceptActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            } catch (e: HttpException) {
                showError(mapHttpError(e))
                resetButton()
            } catch (_: IOException) {
                showError(getString(R.string.error_check_internet))
                resetButton()
            } catch (_: Exception) {
                showError(getString(R.string.invite_error_generic))
                resetButton()
            }
        }
    }

    private fun mapHttpError(exception: HttpException): String {
        if (exception.code() == 404) return getString(R.string.invite_not_found)
        return ApiErrors.messageFrom(exception) ?: getString(R.string.invite_error_generic)
    }

    private fun showError(message: String) {
        tvInviteError.text = message
        tvInviteError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvInviteError.visibility = View.GONE
    }

    private fun resetButton() {
        btnAcceptInvite.isEnabled = true
        btnAcceptInvite.setText(R.string.invite_accept_button)
    }

    companion object {
        const val EXTRA_CODE = "extra_invite_code"
    }
}
