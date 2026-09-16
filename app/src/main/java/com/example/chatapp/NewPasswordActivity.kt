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

class NewPasswordActivity : AppCompatActivity() {

    private lateinit var identifier: String
    private lateinit var code: String
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvNewPasswordError: TextView
    private lateinit var btnResetPassword: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)
        setupAuthHeader(
            R.string.ui_nova_senha,
            R.string.auth_new_password_description,
            showBack = true,
        )

        identifier = intent.getStringExtra(VerifyResetCodeActivity.EXTRA_IDENTIFIER).orEmpty()
        code = intent.getStringExtra(VerifyResetCodeActivity.EXTRA_CODE).orEmpty()

        tilNewPassword = findViewById(R.id.tilNewPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError)
        btnResetPassword = findViewById(R.id.btnResetPassword)

        btnResetPassword.setOnClickListener { attemptReset() }

        etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptReset()
                true
            } else false
        }
    }

    private fun attemptReset() {
        hideError()
        tilNewPassword.error = null
        tilConfirmPassword.error = null

        val newPassword = etNewPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (newPassword.length < 8) {
            tilNewPassword.error = getString(R.string.reset_error_password_min_length)
            return
        }
        if (newPassword != confirmPassword) {
            tilConfirmPassword.error = getString(R.string.reset_error_password_mismatch)
            return
        }

        btnResetPassword.isEnabled = false
        btnResetPassword.setText(R.string.reset_saving_password)

        lifecycleScope.launch {
            try {
                RetrofitClient.api.confirmPasswordReset(
                    mapOf(
                        "identifier" to identifier,
                        "code" to code,
                        "new_password" to newPassword
                    )
                )
                Toast.makeText(
                    this@NewPasswordActivity,
                    R.string.reset_password_changed_message,
                    Toast.LENGTH_LONG
                ).show()
                goToLogin()
            } catch (e: HttpException) {
                handleResetHttpError(e)
            } catch (_: IOException) {
                showError(getString(R.string.reset_error_connection))
            } catch (_: Exception) {
                showError(getString(R.string.reset_error_server))
            } finally {
                btnResetPassword.isEnabled = true
                btnResetPassword.setText(R.string.ui_redefinir_senha)
            }
        }
    }

    private fun handleResetHttpError(exception: HttpException) {
        when (exception.code()) {
            429 -> showError(getString(R.string.reset_error_rate_limit))
            400 -> {
                val message = ApiErrors.messageFrom(exception)
                if (message == getString(R.string.reset_error_code_invalid)) {
                    showError(getString(R.string.reset_error_code_invalid))
                } else if (message != null) {
                    tilNewPassword.error = message
                    showError(message)
                } else {
                    showError(getString(R.string.reset_error_server))
                }
            }
            else -> showError(getString(R.string.reset_error_server))
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        tvNewPasswordError.text = message
        tvNewPasswordError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvNewPasswordError.visibility = View.GONE
    }
}
