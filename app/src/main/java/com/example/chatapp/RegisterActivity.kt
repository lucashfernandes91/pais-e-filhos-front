package com.example.chatapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilBirthDate: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilPasswordConfirm: TextInputLayout
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etBirthDate: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etPasswordConfirm: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private var selectedBirthDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Find TextInputLayouts (parents of the EditTexts)
        tilFirstName = findTilFor(R.id.etRegFirstName)
        tilLastName = findTilFor(R.id.etRegLastName)
        tilUsername = findTilFor(R.id.etRegUsername)
        tilBirthDate = findTilFor(R.id.etRegBirthDate)
        tilEmail = findTilFor(R.id.etRegEmail)
        tilPassword = findTilFor(R.id.etRegPassword)
        tilPasswordConfirm = findTilFor(R.id.etRegPasswordConfirm)

        etFirstName = findViewById(R.id.etRegFirstName)
        etLastName = findViewById(R.id.etRegLastName)
        etUsername = findViewById(R.id.etRegUsername)
        etBirthDate = findViewById(R.id.etRegBirthDate)
        etEmail = findViewById(R.id.etRegEmail)
        etPassword = findViewById(R.id.etRegPassword)
        etPasswordConfirm = findViewById(R.id.etRegPasswordConfirm)
        btnRegister = findViewById(R.id.btnRegister)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            // Vindo do onboarding esta tela é a raiz da task: voltar
            // precisa levar para o login, não fechar o app.
            if (isTaskRoot) {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }

        setupInlineValidation()
        btnRegister.setOnClickListener { attemptRegister() }
    }

    // ──────────────────────────────────────────────
    // Inline validation — real-time feedback
    // ──────────────────────────────────────────────

    private fun setupInlineValidation() {
        etFirstName.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateNameField(etFirstName, tilFirstName, R.string.register_first_name_required)
        }
        etLastName.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateNameField(etLastName, tilLastName, R.string.register_last_name_required)
        }

        // Username: validate on blur
        etUsername.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateUsernameField()
        }

        // Birth date picker and required email validation
        etBirthDate.setOnClickListener {
            val initial = Calendar.getInstance().apply { add(Calendar.YEAR, -18) }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedBirthDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                    etBirthDate.setText(
                        String.format(Locale.forLanguageTag("pt-BR"), "%02d/%02d/%04d", day, month + 1, year)
                    )
                    setValid(tilBirthDate)
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        etEmail.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateEmailField()
        }

        // Password: validate on text change (live strength feedback)
        etPassword.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                when {
                    text.isEmpty() -> clearState(tilPassword)
                    text.length < 8 -> setWarning(tilPassword, getString(R.string.register_password_min_length))
                    else -> setValid(tilPassword)
                }
                // Re-validate confirm if it has content
                if (etPasswordConfirm.text.toString().isNotEmpty()) validateConfirmField()
            }
        })

        // Confirm: validate on blur
        etPasswordConfirm.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateConfirmField()
        }
    }

    private fun validateUsernameField(): Boolean {
        val text = etUsername.text.toString().trim()
        return when {
            text.isEmpty() -> { setError(tilUsername, getString(R.string.register_username_required)); false }
            text.length < 5 -> { setError(tilUsername, getString(R.string.register_username_min_length)); false }
            !text.matches(Regex("^[a-zA-Z0-9._]+$")) -> {
                setError(tilUsername, getString(R.string.register_username_allowed_chars)); false
            }
            else -> { setValid(tilUsername); true }
        }
    }

    private fun validateEmailField(): Boolean {
        val text = etEmail.text.toString().trim()
        if (text.isEmpty()) {
            setError(tilEmail, getString(R.string.register_email_required))
            return false
        }
        val result = InputValidator.validateEmail(text)
        return if (result.isValid) { setValid(tilEmail); true }
        else { setError(tilEmail, result.errorMessage ?: getString(R.string.register_email_invalid)); false }
    }

    private fun validatePasswordField(): Boolean {
        val text = etPassword.text.toString()
        return when {
            text.isEmpty() -> { setError(tilPassword, getString(R.string.register_password_required)); false }
            text.length < 8 -> { setError(tilPassword, getString(R.string.register_password_min_length)); false }
            else -> { setValid(tilPassword); true }
        }
    }

    private fun validateNameField(
        field: TextInputEditText,
        layout: TextInputLayout,
        errorMessage: Int
    ): Boolean {
        return if (field.text.toString().trim().isEmpty()) {
            setError(layout, getString(errorMessage))
            false
        } else {
            setValid(layout)
            true
        }
    }

    private fun validateBirthDateField(): Boolean {
        return if (selectedBirthDate.isNullOrEmpty()) {
            setError(tilBirthDate, getString(R.string.register_birth_date_required))
            false
        } else {
            setValid(tilBirthDate)
            true
        }
    }

    private fun validateConfirmField(): Boolean {
        val password = etPassword.text.toString()
        val confirm = etPasswordConfirm.text.toString()
        return when {
            confirm.isEmpty() -> { setError(tilPasswordConfirm, getString(R.string.register_confirm_password_required)); false }
            confirm != password -> { setError(tilPasswordConfirm, getString(R.string.register_password_mismatch)); false }
            else -> { setValid(tilPasswordConfirm); true }
        }
    }

    // ──────────────────────────────────────────────
    // Submit — validate all + register
    // ──────────────────────────────────────────────

    private fun attemptRegister() {
        val firstNameOk = validateNameField(etFirstName, tilFirstName, R.string.register_first_name_required)
        val lastNameOk = validateNameField(etLastName, tilLastName, R.string.register_last_name_required)
        val usernameOk = validateUsernameField()
        val birthDateOk = validateBirthDateField()
        val emailOk = validateEmailField()
        val passwordOk = validatePasswordField()
        val confirmOk = validateConfirmField()

        if (!firstNameOk || !lastNameOk || !usernameOk || !birthDateOk || !emailOk || !passwordOk || !confirmOk) {
            btnRegister.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }

        btnRegister.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        btnRegister.isEnabled = false
        btnRegister.setText(R.string.register_creating_account)

        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val birthDate = selectedBirthDate.orEmpty()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.registerUser(
                    mapOf(
                        "first_name" to firstName,
                        "last_name" to lastName,
                        "username" to username,
                        "birth_date" to birthDate,
                        "email" to email,
                        "password" to password
                    )
                )

                val accessToken = (response["access"] as? String) ?: ""
                val refreshToken = (response["refresh"] as? String) ?: ""
                val returnedUsername = (response["username"] as? String) ?: username
                val conversationId = (response["conversation_id"] as? Number)?.toInt()
                    ?: PrefsHelper.NO_CONVERSATION_ID

                if (accessToken.isNotEmpty() && conversationId > PrefsHelper.NO_CONVERSATION_ID) {
                    PrefsHelper.saveCredentials(
                        this@RegisterActivity, accessToken, refreshToken, returnedUsername
                    )
                    PrefsHelper.saveConversationId(this@RegisterActivity, conversationId)
                    PrefsHelper.setWelcomePending(this@RegisterActivity, true)
                    RetrofitClient.init(this@RegisterActivity)

                    Toast.makeText(this@RegisterActivity, getString(R.string.register_account_created), Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showError(getString(R.string.register_conversation_start_error))
                }
            } catch (e: HttpException) {
                handleServerError(e)
            } catch (_: IOException) {
                showError(getString(R.string.login_error_connection))
            } catch (e: Exception) {
                showError(getString(R.string.register_generic_error, e.message.orEmpty()))
            }
        }
    }

    /**
     * B4: exibe a mensagem real do servidor e a ancora no campo certo
     * ("Nome de usuário já existe" → campo de usuário, etc.).
     */
    private fun handleServerError(exception: HttpException) {
        val message = ApiErrors.messageFrom(exception)
        if (message == null) {
            showError(getString(R.string.register_generic_error, "HTTP ${exception.code()}"))
            return
        }

        val lower = message.lowercase(Locale.forLanguageTag("pt-BR"))
        val field = when {
            "usuário" in lower || "usuario" in lower -> tilUsername
            "email" in lower || "e-mail" in lower -> tilEmail
            "senha" in lower || "password" in lower -> tilPassword
            "nascimento" in lower -> tilBirthDate
            "sobrenome" in lower -> tilLastName
            "nome" in lower -> tilFirstName
            else -> null
        }
        field?.let { setError(it, message) }
        showError(message)
    }

    // ──────────────────────────────────────────────
    // Visual state helpers
    // ──────────────────────────────────────────────

    private fun setError(til: TextInputLayout, msg: String) {
        til.error = msg
        til.isErrorEnabled = true
    }

    private fun setValid(til: TextInputLayout) {
        til.error = null
        til.isErrorEnabled = false
        til.helperText = "✓"
        til.setHelperTextTextAppearance(R.style.TextAppearance_Valid)
    }

    private fun setWarning(til: TextInputLayout, msg: String) {
        til.error = null
        til.isErrorEnabled = false
        til.helperText = msg
        til.setHelperTextTextAppearance(R.style.TextAppearance_Warning)
    }

    private fun clearState(til: TextInputLayout) {
        til.error = null
        til.isErrorEnabled = false
        til.helperText = null
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        btnRegister.isEnabled = true
        btnRegister.setText(R.string.ui_criar_conta)
    }

    /** Find the TextInputLayout parent of a TextInputEditText by its id */
    private fun findTilFor(editTextId: Int): TextInputLayout {
        val editText = findViewById<TextInputEditText>(editTextId)
        return editText.parent.parent as TextInputLayout
    }

    /** Simplified TextWatcher — only override what you need */
    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}
    }
}
