package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

class LoginActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvLoginError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        RetrofitClient.init(this)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvLoginError = findViewById(R.id.tvLoginError)
        tilEmail = etEmail.parent.parent as TextInputLayout
        tilPassword = etPassword.parent.parent as TextInputLayout

        setupInlineValidation()

        btnLogin.setOnClickListener {
            hideKeyboard()
            attemptLogin()
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                attemptLogin()
                true
            } else false
        }

        findViewById<MaterialButton>(R.id.tvCreateAccount)?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.tvForgotPasswordLink)?.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        val existingToken = PrefsHelper.getAuthToken(this)
        if (existingToken.isNotEmpty()) {
            validateStoredSession(existingToken)
        }
    }

    // ──────────────────────────────────────────────
    // Inline validation
    // ──────────────────────────────────────────────

    private fun setupInlineValidation() {
        val clearLoginErrorWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideLoginError()
                clearState(tilEmail)
                clearState(tilPassword)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }

        etEmail.addTextChangedListener(clearLoginErrorWatcher)
        etPassword.addTextChangedListener(clearLoginErrorWatcher)

        etEmail.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateUsernameField()
        }

        etPassword.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePasswordField()
        }
    }

    private fun validateUsernameField(): Boolean {
        val text = etEmail.text.toString().trim()
        return when {
            text.isEmpty() -> { setError(tilEmail, "Usuário obrigatório"); false }
            text.length < 3 -> { setError(tilEmail, "Mínimo 3 caracteres"); false }
            else -> { clearState(tilEmail); true }
        }
    }

    private fun validatePasswordField(): Boolean {
        val text = etPassword.text.toString()
        return when {
            text.isEmpty() -> { setError(tilPassword, "Senha obrigatória"); false }
            else -> { clearState(tilPassword); true }
        }
    }

    // ──────────────────────────────────────────────
    // Login flow
    // ──────────────────────────────────────────────

    private fun validateStoredSession(token: String) {
        btnLogin.isEnabled = false
        btnLogin.setText(R.string.login_validating_session)

        lifecycleScope.launch {
            try {
                RetrofitClient.api.getProfile("Bearer $token")
                if (loadConversationData(token)) {
                    goToMain()
                } else {
                    showError("Nao foi possivel iniciar sua conversa")
                }
            } catch (_: Exception) {
                PrefsHelper.clearSession(this@LoginActivity)
                btnLogin.isEnabled = true
                btnLogin.setText(R.string.ui_entrar)
            }
        }
    }

    private fun attemptLogin() {
        hideLoginError()
        val usernameOk = validateUsernameField()
        val passwordOk = validatePasswordField()

        if (!usernameOk || !passwordOk) {
            btnLogin.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }

        btnLogin.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        btnLogin.isEnabled = false
        btnLogin.setText(R.string.login_connecting)

        val username = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.obtainToken(
                    mapOf("username" to username, "password" to password)
                )

                val accessToken = (response["access"] as? String) ?: ""
                val refreshToken = (response["refresh"] as? String) ?: ""

                if (accessToken.isNotEmpty()) {
                    PrefsHelper.saveCredentials(
                        this@LoginActivity, accessToken, refreshToken, username
                    )
                    RetrofitClient.init(this@LoginActivity)
                    if (loadConversationData(accessToken)) {
                        goToMain()
                    } else {
                        showLoginError(getString(R.string.login_error_conversation))
                    }
                } else {
                    showLoginError(getString(R.string.login_error_token_missing))
                }
            } catch (e: HttpException) {
                handleLoginHttpError(e)
            } catch (_: IOException) {
                showLoginError(getString(R.string.login_error_connection))
            } catch (e: Exception) {
                showLoginError(getString(R.string.login_error_server))
            }
        }
    }

    /**
     * Após login, busca conversas do usuário para:
     * - Salvar o conversationId real
     * - Salvar o nome do outro pai
     * - Salvar nomes dos filhos
     */
    private suspend fun loadConversationData(token: String): Boolean {
        return try {
            val conversations = RetrofitClient.api.getConversations("Bearer $token")
            val conv = conversations.firstOrNull() ?: return false
            PrefsHelper.saveConversationId(this, conv.id)

            val otherParent = conv.participants.firstOrNull { !it.is_me }
            if (otherParent != null) {
                PrefsHelper.saveOtherParentName(this, otherParent.username)
            }

            if (conv.children.isNotEmpty()) {
                val childrenNames = conv.children.joinToString(", ") { it.name }
                PrefsHelper.saveChildrenNames(this, childrenNames)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────
    // Visual state helpers
    // ──────────────────────────────────────────────

    private fun setError(til: TextInputLayout, msg: String) {
        til.error = msg
        til.isErrorEnabled = true
    }

    private fun clearState(til: TextInputLayout) {
        til.error = null
        til.isErrorEnabled = false
        til.helperText = null
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        resetLoginButton()
        // Shake animation on error
        tilEmail.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )
    }

    private fun handleLoginHttpError(exception: HttpException) {
        when (exception.code()) {
            400, 401 -> {
                setError(tilEmail, getString(R.string.login_error_check_user))
                setError(tilPassword, getString(R.string.login_error_check_password))
                showLoginError(getString(R.string.login_error_invalid_credentials))
            }
            429 -> showLoginError(getString(R.string.login_error_too_many_attempts))
            500, 502, 503 -> showLoginError(getString(R.string.login_error_server))
            else -> showLoginError(getString(R.string.login_error_server))
        }
    }

    private fun showLoginError(message: String) {
        tvLoginError.text = message
        tvLoginError.visibility = View.VISIBLE
        resetLoginButton()
        tilEmail.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
        )
    }

    private fun hideLoginError() {
        if (::tvLoginError.isInitialized) {
            tvLoginError.visibility = View.GONE
        }
    }

    private fun resetLoginButton() {
        btnLogin.isEnabled = true
        btnLogin.setText(R.string.ui_entrar)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
