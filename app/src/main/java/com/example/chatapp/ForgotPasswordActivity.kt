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

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilIdentifier: TextInputLayout
    private lateinit var etIdentifier: TextInputEditText
    private lateinit var tvForgotError: TextView
    private lateinit var btnSendCode: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        tilIdentifier = findViewById(R.id.tilIdentifier)
        etIdentifier = findViewById(R.id.etIdentifier)
        tvForgotError = findViewById(R.id.tvForgotError)
        btnSendCode = findViewById(R.id.btnSendCode)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnSendCode.setOnClickListener { attemptSendCode() }

        etIdentifier.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSendCode()
                true
            } else false
        }
    }

    private fun attemptSendCode() {
        hideError()
        val identifier = etIdentifier.text.toString().trim()

        if (identifier.isEmpty()) {
            tilIdentifier.error = getString(R.string.reset_error_identifier_required)
            return
        }
        tilIdentifier.error = null

        btnSendCode.isEnabled = false
        btnSendCode.setText(R.string.reset_sending_code)

        lifecycleScope.launch {
            try {
                RetrofitClient.api.requestPasswordReset(mapOf("identifier" to identifier))
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    R.string.reset_code_sent_message,
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(this@ForgotPasswordActivity, VerifyResetCodeActivity::class.java)
                        .putExtra(EXTRA_IDENTIFIER, identifier)
                )
            } catch (_: IOException) {
                showError(getString(R.string.reset_error_connection))
            } catch (_: HttpException) {
                showError(getString(R.string.reset_error_server))
            } catch (_: Exception) {
                showError(getString(R.string.reset_error_server))
            } finally {
                btnSendCode.isEnabled = true
                btnSendCode.setText(R.string.ui_enviar_codigo)
            }
        }
    }

    private fun showError(message: String) {
        tvForgotError.text = message
        tvForgotError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvForgotError.visibility = View.GONE
    }

    companion object {
        const val EXTRA_IDENTIFIER = "extra_identifier"
    }
}
