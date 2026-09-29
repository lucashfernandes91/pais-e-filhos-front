package com.example.chatapp

import java.util.regex.Pattern

object InputValidator {

    private val emailPattern = Pattern.compile(
        "\\A[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#\$%&'*+/=?^_`{|}~-]+)*" +
            "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}\\z"
    )

    /**
     * Validate email
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email não pode estar vazio")
            !emailPattern.matcher(email).matches() -> ValidationResult(false, "Email inválido")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Result of validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}
