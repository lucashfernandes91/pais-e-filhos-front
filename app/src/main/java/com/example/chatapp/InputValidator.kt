package com.example.chatapp

import java.util.regex.Pattern

object InputValidator {

    /**
     * Validate message content
     */
    fun validateMessage(content: String): ValidationResult {
        return when {
            content.isBlank() -> ValidationResult(false, "Mensagem não pode estar vazia")
            content.trim().length < 1 -> ValidationResult(false, "Mensagem muito curta")
            content.length > 5000 -> ValidationResult(false, "Mensagem muito longa (máx 5000 caracteres)")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Validate email
     */
    fun validateEmail(email: String): ValidationResult {
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        )
        return when {
            email.isBlank() -> ValidationResult(false, "Email não pode estar vazio")
            !emailPattern.matcher(email).matches() -> ValidationResult(false, "Email inválido")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Validate password
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult(false, "Senha não pode estar vazia")
            password.length < 6 -> ValidationResult(false, "Senha muito curta (mín 6 caracteres)")
            password.length > 100 -> ValidationResult(false, "Senha muito longa (máx 100 caracteres)")
            else -> ValidationResult(true, null)
        }
    }

    /**
     * Validate event title
     */
    fun validateEventTitle(title: String): ValidationResult {
        return when {
            title.isBlank() -> ValidationResult(false, "Título não pode estar vazio")
            title.length < 3 -> ValidationResult(false, "Título muito curto")
            title.length > 255 -> ValidationResult(false, "Título muito longo")
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
