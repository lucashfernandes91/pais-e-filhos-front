package com.example.chatapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun acceptsCompleteEmailAddresses() {
        listOf(
            "lucas@example.com",
            "nome.sobrenome+tag@sub.example.com.br",
        ).forEach { email ->
            assertTrue(email, InputValidator.validateEmail(email).isValid)
        }
    }

    @Test
    fun rejectsIncompleteOrMalformedEmailAddresses() {
        listOf(
            "qualquer@coisa",
            "abc@",
            "@example.com",
            "abc@.com",
            "abc@example.",
            "abc..def@example.com",
            "abc@example..com",
            "abc@-example.com",
            "abc@example-.com",
            "abc@example.c",
        ).forEach { email ->
            assertFalse(email, InputValidator.validateEmail(email).isValid)
        }
    }
}
