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

class VerifyResetCodeActivity : AppCompatActivity() {

    private lateinit var identifier: String
    private lateinit var tilCode: TextInputLayout
    private lateinit var etCode: TextInputEditText
    private lateinit var tvVerifyError: TextView
    private lateinit var btnVerifyCode: MaterialButton
    private lateinit var tvResendCode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_reset_code)

        identifier = intent.getStringExtra(ForgotPasswordActivity.EXTRA_IDENTIFIER).orEmpty()

        tilCode = findViewById(R.id.tilCode)
        etCode = findViewById(R.id.etCode)
        tvVerifyError = findViewById(R.id.tvVerifyError)
        btnVerifyCode = findViewById(R.id.btnVerifyCode)
        tvResendCode = findViewById(R.id.tvResendCode)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnVerifyCode.setOnClickListener { attemptVerify() }
        tvResendCode.setOnClickListener { resendCode() }

        etCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptVerify()
                true
            } else false
        }
    }

    private fun attemptVerify() {
        hideError()
        val code = etCode.text.toString().trim()

        if (code.length != 6) {
            tilCode.error = getString(R.string.reset_error_code_required)
            return
        }
        tilCode.error = null

        btnVerifyCode.isEnabled = false
        btnVerifyCode.setText(R.string.reset_verifying_code)

        lifecycleScope.launch {
            try {
                RetrofitClient.api.verifyPasswordResetCode(
                    mapOf("identifier" to identifier, "code" to code)
                )
                startActivity(
                    Intent(this@VerifyResetCodeActivity, NewPasswordActivity::class.java)
                        .putExtra(EXTRA_IDENTIFIER, identifier)
                        .putExtra(EXTRA_CODE, code)
                )
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    showError(getString(R.string.reset_error_rate_limit))
                } else {
                    showError(getString(R.string.reset_error_code_invalid))
                }
            } catch (_: IOException) {
                showError(getString(R.string.reset_error_connection))
            } catch (_: Exception) {
                showError(getString(R.string.reset_error_server))
            } finally {
                btnVerifyCode.isEnabled = true
                btnVerifyCode.setText(R.string.ui_verificar)
            }
        }
    }

    private fun resendCode() {
        lifecycleScope.launch {
            try {
                RetrofitClient.api.requestPasswordReset(mapOf("identifier" to identifier))
                Toast.makeText(
                    this@VerifyResetCodeActivity,
                    R.string.reset_code_resent_message,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this@VerifyResetCodeActivity,
                    R.string.reset_error_connection,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showError(message: String) {
        tvVerifyError.text = message
        tvVerifyError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvVerifyError.visibility = View.GONE
    }

    companion object {
        const val EXTRA_IDENTIFIER = "extra_identifier"
        const val EXTRA_CODE = "extra_code"
    }
}
