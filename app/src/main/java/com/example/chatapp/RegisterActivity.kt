package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etUsername = findViewById<TextInputEditText>(R.id.etRegUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etRegEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etRegPassword)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etRegPasswordConfirm)
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnRegister.setOnClickListener {
            val username = etUsername.text?.toString()?.trim() ?: ""
            val email = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""
            val passwordConfirm = etPasswordConfirm.text?.toString() ?: ""

            if (username.length < 3) {
                Toast.makeText(this, "Nome deve ter pelo menos 3 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Senha deve ter pelo menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != passwordConfirm) {
                Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Criando conta..."

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.api.registerUser(
                        mapOf(
                            "username" to username,
                            "email" to email,
                            "password" to password
                        )
                    )

                    val accessToken = (response["access"] as? String) ?: ""
                    val refreshToken = (response["refresh"] as? String) ?: ""
                    val returnedUsername = (response["username"] as? String) ?: username

                    if (accessToken.isNotEmpty()) {
                        PrefsHelper.saveCredentials(
                            this@RegisterActivity,
                            accessToken,
                            refreshToken,
                            returnedUsername
                        )
                        RetrofitClient.init(this@RegisterActivity)

                        Toast.makeText(this@RegisterActivity, "Conta criada!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        showError("Erro ao criar conta")
                    }
                } catch (e: Exception) {
                    showError("Erro: ${e.message}")
                }
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        findViewById<MaterialButton>(R.id.btnRegister).apply {
            isEnabled = true
            text = "Criar conta"
        }
    }
}
