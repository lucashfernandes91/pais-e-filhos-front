package com.example.chatapp

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
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
    private lateinit var registerProgressIndicator: ProgressBar
    private lateinit var tvRegisterError: TextView
    private lateinit var registerScroll: ScrollView
    private lateinit var cbAdultDeclaration: MaterialCheckBox
    private lateinit var cbLegalAcceptance: MaterialCheckBox
    private var selectedBirthDate: String? = null
    private var currentStep = 0
    private var isSubmitting = false
    private var responsiveCompact: Boolean? = null

    private data class ValidationTarget(val view: View, val valid: Boolean, val step: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        setupAuthHeader(
            R.string.ui_criar_conta,
            R.string.auth_register_description,
            showBack = true,
            onBack = ::navigateBack,
        )

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
        registerProgressIndicator = findViewById(R.id.registerProgressIndicator)
        tvRegisterError = findViewById(R.id.tvRegisterError)
        registerScroll = findViewById(R.id.registerScroll)
        cbAdultDeclaration = findViewById(R.id.cbAdultDeclaration)
        cbLegalAcceptance = findViewById(R.id.cbLegalAcceptance)

        selectedBirthDate = savedInstanceState?.getString(STATE_SELECTED_BIRTH_DATE)
        selectedBirthDate?.let(::renderBirthDate)

        findViewById<View>(R.id.tvTermsLink).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocuments.TERMS_URL)))
        }
        findViewById<View>(R.id.tvPrivacyLink).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocuments.PRIVACY_URL)))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })

        setupInlineValidation()
        setupKeyboardNavigation()
        currentStep = savedInstanceState?.getInt(STATE_STEP, 0)?.coerceIn(0, 2) ?: 0
        btnRegister.setOnClickListener { advanceStep() }
        findViewById<View>(R.id.btnRegisterPrevious).setOnClickListener { navigateBack() }
        findViewById<TextView>(R.id.tvExistingAccount).setOnClickListener { openLogin() }
        setupResponsiveLayout()
        renderStep()
    }

    private fun setupResponsiveLayout() {
        val nameRow = findViewById<LinearLayout>(R.id.registerNameRow)
        nameRow.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            adaptResponsiveLayout(right - left)
        }
        nameRow.post { adaptResponsiveLayout(nameRow.width) }
    }

    private fun adaptResponsiveLayout(widthPx: Int) {
        if (widthPx <= 0) return
        val compact = widthPx < (360 * resources.displayMetrics.density).toInt() ||
            resources.configuration.fontScale >= 1.2f
        if (responsiveCompact == compact) return
        responsiveCompact = compact

        val nameRow = findViewById<LinearLayout>(R.id.registerNameRow)
        val firstColumn = findViewById<LinearLayout>(R.id.registerFirstNameColumn)
        val lastColumn = findViewById<LinearLayout>(R.id.registerLastNameColumn)
        nameRow.orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        val firstParams = firstColumn.layoutParams as LinearLayout.LayoutParams
        val lastParams = lastColumn.layoutParams as LinearLayout.LayoutParams
        if (compact) {
            firstParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            firstParams.weight = 0f
            firstParams.setMargins(0, 0, 0, 0)
            lastParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            lastParams.weight = 0f
            lastParams.setMargins(0, 0, 0, 0)
        } else {
            firstParams.width = 0
            firstParams.weight = 1f
            firstParams.setMargins(0, 0, resources.getDimensionPixelSize(R.dimen.spacing_s), 0)
            lastParams.width = 0
            lastParams.weight = 1f
            lastParams.setMargins(0, 0, 0, 0)
        }
        firstColumn.layoutParams = firstParams
        lastColumn.layoutParams = lastParams

        val legalLinks = findViewById<LinearLayout>(R.id.registerLegalLinks)
        val terms = findViewById<View>(R.id.tvTermsLink)
        val privacy = findViewById<View>(R.id.tvPrivacyLink)
        legalLinks.orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val termsParams = terms.layoutParams as LinearLayout.LayoutParams
        val privacyParams = privacy.layoutParams as LinearLayout.LayoutParams
        if (compact) {
            termsParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            termsParams.weight = 0f
            termsParams.setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.spacing_xs))
            privacyParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            privacyParams.weight = 0f
            privacyParams.setMargins(0, 0, 0, 0)
        } else {
            termsParams.width = 0
            termsParams.weight = 1f
            termsParams.setMargins(0, 0, resources.getDimensionPixelSize(R.dimen.spacing_xs), 0)
            privacyParams.width = 0
            privacyParams.weight = 1f
            privacyParams.setMargins(resources.getDimensionPixelSize(R.dimen.spacing_xs), 0, 0, 0)
        }
        terms.layoutParams = termsParams
        privacy.layoutParams = privacyParams
    }

    private fun openLogin() {
        if (isSubmitting) return
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun setupKeyboardNavigation() {
        etFirstName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etLastName.requestFocus()
                true
            } else false
        }
        etLastName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etBirthDate.performClick()
                true
            } else false
        }
        etUsername.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etEmail.requestFocus()
                true
            } else false
        }
        etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPassword.requestFocus()
                true
            } else false
        }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPasswordConfirm.requestFocus()
                true
            } else false
        }
        etPasswordConfirm.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (isDone) {
                advanceStep()
                true
            } else false
        }
    }

    private fun navigateBack() {
        if (isSubmitting) return
        if (currentStep > 0) {
            currentStep--
            renderStep()
            return
        }
        if (isTaskRoot) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

    private fun advanceStep() {
        if (isSubmitting) return
        hideFormError()
        if (currentStep == 2) {
            attemptRegister()
            return
        }
        val checks = if (currentStep == 0) {
            listOf(
                ValidationTarget(etFirstName, validateNameField(etFirstName, tilFirstName, R.string.register_first_name_required), 0),
                ValidationTarget(etLastName, validateNameField(etLastName, tilLastName, R.string.register_last_name_required), 0),
                ValidationTarget(etBirthDate, validateBirthDateField(), 0),
            )
        } else {
            listOf(
                ValidationTarget(etUsername, validateUsernameField(), 1),
                ValidationTarget(etEmail, validateEmailField(), 1),
                ValidationTarget(etPassword, validatePasswordField(), 1),
                ValidationTarget(etPasswordConfirm, validateConfirmField(), 1),
            )
        }
        if (checks.all { it.valid }) {
            currentStep++
            renderStep()
        } else {
            focusFirstInvalid(checks)
        }
    }

    private fun renderStep() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        listOf(R.id.registerPersonalStep, R.id.registerAccessStep, R.id.registerDeclarationsStep)
            .forEachIndexed { index, id ->
                findViewById<View>(id).visibility = if (index == currentStep) View.VISIBLE else View.GONE
            }
        val title = getString(listOf(
            R.string.register_step_personal, R.string.register_step_access, R.string.register_step_declarations
        )[currentStep])
        findViewById<TextView>(R.id.registerStepTitle).text =
            getString(R.string.register_step_progress, currentStep + 1, title)
        findViewById<ProgressBar>(R.id.registerProgress).progress = currentStep + 1
        findViewById<View>(R.id.btnRegisterPrevious).visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        btnRegister.setText(if (currentStep == 2) R.string.ui_criar_conta else R.string.ui_continuar)
        registerScroll.post {
            registerScroll.scrollTo(0, 0)
        }
    }

    private fun focusFirstInvalid(checks: List<ValidationTarget>) {
        val target = checks.firstOrNull { !it.valid } ?: return
        currentStep = target.step
        renderStep()
        target.view.post {
            if (target.view == etBirthDate) target.view.isFocusableInTouchMode = true
            target.view.requestFocus()
            val rect = Rect()
            target.view.getDrawingRect(rect)
            registerScroll.offsetDescendantRectToMyCoords(target.view, rect)
            registerScroll.smoothScrollTo(0, maxOf(0, rect.top - resources.getDimensionPixelSize(R.dimen.spacing_m)))
        }
    }

    // ──────────────────────────────────────────────
    // Inline validation — real-time feedback
    // ──────────────────────────────────────────────

    private fun setupInlineValidation() {
        etFirstName.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideFormError()
                if (tilFirstName.isErrorEnabled || !s.isNullOrBlank()) {
                    validateNameField(etFirstName, tilFirstName, R.string.register_first_name_required)
                }
            }
        })
        etLastName.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideFormError()
                if (tilLastName.isErrorEnabled || !s.isNullOrBlank()) {
                    validateNameField(etLastName, tilLastName, R.string.register_last_name_required)
                }
            }
        })
        etUsername.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideFormError()
                if (tilUsername.isErrorEnabled || !s.isNullOrBlank()) validateUsernameField()
            }
        })
        etEmail.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideFormError()
                if (tilEmail.isErrorEnabled || !s.isNullOrBlank()) validateEmailField()
            }
        })
        etPasswordConfirm.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hideFormError()
                if (tilPasswordConfirm.isErrorEnabled || !s.isNullOrEmpty()) validateConfirmField()
            }
        })

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
            val latestAllowed = Calendar.getInstance().apply { add(Calendar.YEAR, -18) }
            val initial = parseBirthDate(selectedBirthDate)
                ?.takeIf { !it.after(latestAllowed) }
                ?: latestAllowed
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedBirthDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                    renderBirthDate(selectedBirthDate.orEmpty())
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setTitle(R.string.register_birth_date_dialog_title)
                datePicker.maxDate = latestAllowed.timeInMillis
                datePicker.calendarViewShown = true
                datePicker.spinnersShown = false
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
                    text.all(Char::isDigit) -> setWarning(tilPassword, getString(R.string.register_password_numeric))
                    isPasswordSimilarToPersonalData(text) -> setWarning(tilPassword, getString(R.string.register_password_personal_data))
                    else -> setWarning(tilPassword, getString(R.string.register_password_server_validation))
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedBirthDate?.let { outState.putString(STATE_SELECTED_BIRTH_DATE, it) }
        outState.putInt(STATE_STEP, currentStep)
        outState.putString(STATE_PASSWORD, etPassword.text.toString())
        outState.putString(STATE_PASSWORD_CONFIRM, etPasswordConfirm.text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        etPassword.setText(savedInstanceState.getString(STATE_PASSWORD).orEmpty())
        etPasswordConfirm.setText(savedInstanceState.getString(STATE_PASSWORD_CONFIRM).orEmpty())
        selectedBirthDate?.let(::renderBirthDate)
        renderStep()
    }

    private fun renderBirthDate(birthDate: String) {
        val parts = birthDate.split("-")
        if (parts.size != 3) {
            selectedBirthDate = null
            return
        }

        val year = parts[0].toIntOrNull()
        val month = parts[1].toIntOrNull()
        val day = parts[2].toIntOrNull()
        if (year == null || month == null || day == null) {
            selectedBirthDate = null
            return
        }

        etBirthDate.setText(
            String.format(
                Locale.forLanguageTag("pt-BR"),
                "%02d/%02d/%04d",
                day,
                month,
                year,
            )
        )
        setValid(tilBirthDate)
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
            text.all(Char::isDigit) -> { setError(tilPassword, getString(R.string.register_password_numeric)); false }
            isPasswordSimilarToPersonalData(text) -> {
                setError(tilPassword, getString(R.string.register_password_personal_data)); false
            }
            else -> {
                setWarning(tilPassword, getString(R.string.register_password_server_validation))
                true
            }
        }
    }

    private fun isPasswordSimilarToPersonalData(password: String): Boolean {
        val normalizedPassword = password.normalizeForComparison()
        if (normalizedPassword.isEmpty()) return false

        return listOf(
            etFirstName.text.toString(),
            etLastName.text.toString(),
            etUsername.text.toString(),
            etEmail.text.toString().substringBefore("@"),
        )
            .map { it.normalizeForComparison() }
            .filter { it.length >= 3 }
            .any { normalizedPassword.contains(it) }
    }

    private fun String.normalizeForComparison(): String =
        lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

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
        val birthDate = selectedBirthDate
        return when {
            birthDate.isNullOrEmpty() -> {
                setError(tilBirthDate, getString(R.string.register_birth_date_required))
                false
            }
            !isAdult(birthDate) -> {
                setError(tilBirthDate, getString(R.string.register_adult_required))
                false
            }
            else -> {
                setValid(tilBirthDate)
                true
            }
        }
    }

    private fun isAdult(birthDate: String): Boolean {
        val birth = parseBirthDate(birthDate) ?: return false
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (
            today.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
            (today.get(Calendar.MONTH) == birth.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
        ) {
            age--
        }
        return age >= 18
    }

    private fun parseBirthDate(value: String?): Calendar? {
        val parts = value?.split("-") ?: return null
        if (parts.size != 3) return null
        return try {
            Calendar.getInstance().apply {
                setLenient(false)
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }
        } catch (_: Exception) {
            null
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
        if (isSubmitting) return
        hideFormError()
        val firstNameOk = validateNameField(etFirstName, tilFirstName, R.string.register_first_name_required)
        val lastNameOk = validateNameField(etLastName, tilLastName, R.string.register_last_name_required)
        val usernameOk = validateUsernameField()
        val birthDateOk = validateBirthDateField()
        val emailOk = validateEmailField()
        val passwordOk = validatePasswordField()
        val confirmOk = validateConfirmField()
        val adultDeclarationOk = cbAdultDeclaration.isChecked
        if (!adultDeclarationOk) {
            cbAdultDeclaration.error = getString(R.string.register_adult_declaration_required)
        } else {
            cbAdultDeclaration.error = null
        }
        val legalOk = cbLegalAcceptance.isChecked
        if (!legalOk) {
            cbLegalAcceptance.error = getString(R.string.register_legal_acceptance_required)
        } else {
            cbLegalAcceptance.error = null
        }

        val checks = listOf(
            ValidationTarget(etFirstName, firstNameOk, 0),
            ValidationTarget(etLastName, lastNameOk, 0),
            ValidationTarget(etBirthDate, birthDateOk, 0),
            ValidationTarget(etUsername, usernameOk, 1),
            ValidationTarget(etEmail, emailOk, 1),
            ValidationTarget(etPassword, passwordOk, 1),
            ValidationTarget(etPasswordConfirm, confirmOk, 1),
            ValidationTarget(cbAdultDeclaration, adultDeclarationOk, 2),
            ValidationTarget(cbLegalAcceptance, legalOk, 2),
        )
        if (checks.any { !it.valid }) {
            focusFirstInvalid(checks)
            btnRegister.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }

        btnRegister.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        isSubmitting = true
        btnRegister.isEnabled = false
        btnRegister.setText(R.string.register_creating_account)
        setRegisterLoading(true)

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
                        "password" to password,
                        "terms_version" to LegalDocuments.TERMS_VERSION,
                        "privacy_version" to LegalDocuments.PRIVACY_VERSION,
                        "accept_terms" to "true",
                        "accept_privacy" to "true",
                        "declare_adult" to "true"
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
        field?.let {
            isSubmitting = false
            btnRegister.isEnabled = true
            btnRegister.setText(if (currentStep == 2) R.string.ui_criar_conta else R.string.ui_continuar)
            setRegisterLoading(false)
            currentStep = if (it == tilFirstName || it == tilLastName || it == tilBirthDate) 0 else 1
            renderStep()
            setError(it, message)
            hideFormError()
            it.post {
                it.requestFocus()
                scrollToView(it)
            }
            return
        }
        showError(message)
    }

    // ──────────────────────────────────────────────
    // Visual state helpers
    // ──────────────────────────────────────────────

    private fun setError(til: TextInputLayout, msg: String) {
        til.helperText = getString(R.string.form_feedback_placeholder)
        til.error = msg
        til.isErrorEnabled = true
    }

    private fun setValid(til: TextInputLayout) {
        til.error = null
        til.isErrorEnabled = false
        til.helperText = when (til) {
            tilBirthDate -> getString(R.string.register_birth_date_valid)
            tilEmail -> getString(R.string.register_email_valid)
            tilUsername -> getString(R.string.register_username_valid)
            tilPasswordConfirm -> getString(R.string.register_password_confirm_valid)
            else -> getString(R.string.register_field_valid)
        }
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
        til.helperText = getString(R.string.form_feedback_placeholder)
    }

    private fun showError(msg: String) {
        isSubmitting = false
        tvRegisterError.text = msg
        tvRegisterError.visibility = View.VISIBLE
        tvRegisterError.post { scrollToView(tvRegisterError) }
        btnRegister.isEnabled = true
        btnRegister.setText(if (currentStep == 2) R.string.ui_criar_conta else R.string.ui_continuar)
        setRegisterLoading(false)
    }

    private fun hideFormError() {
        tvRegisterError.visibility = View.GONE
        tvRegisterError.text = null
    }

    private fun setRegisterLoading(loading: Boolean) {
        registerProgressIndicator.animate()
            .alpha(if (loading) 1f else 0f)
            .setDuration(resources.getInteger(R.integer.motion_duration_short).toLong())
            .start()
    }

    private fun scrollToView(view: View) {
        val rect = Rect()
        view.getDrawingRect(rect)
        registerScroll.offsetDescendantRectToMyCoords(view, rect)
        registerScroll.smoothScrollTo(0, maxOf(0, rect.top - resources.getDimensionPixelSize(R.dimen.spacing_m)))
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

    companion object {
        private const val STATE_SELECTED_BIRTH_DATE = "selected_birth_date"
        private const val STATE_STEP = "register_step"
        private const val STATE_PASSWORD = "register_password"
        private const val STATE_PASSWORD_CONFIRM = "register_password_confirm"
    }
}
