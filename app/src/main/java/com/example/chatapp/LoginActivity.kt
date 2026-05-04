package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se já tem token, vai direto para MainActivity
        val existingToken = PrefsHelper.getAuthToken(this)
        if (existingToken.isNotEmpty()) {
            RetrofitClient.init(this)
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {
            hideKeyboard()
            login()
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                login()
                true
            } else false
        }

        findViewById<android.widget.TextView>(R.id.tvCreateAccount)?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is TextInputEditText) {
                val outRect = android.graphics.Rect()
                focused.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun login() {
        val username = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Usuário e senha obrigatórios", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "Conectando..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.obtainToken(
                    mapOf("username" to username, "password" to password)
                )

                val accessToken = (response["access"] as? String) ?: ""
                val refreshToken = (response["refresh"] as? String) ?: ""

                if (accessToken.isNotEmpty()) {
                    // Salvar credenciais com refresh token
                    PrefsHelper.saveCredentials(
                        this@LoginActivity,
                        accessToken,
                        refreshToken,
                        username
                    )

                    // Inicializar Retrofit com AuthInterceptor
                    RetrofitClient.init(this@LoginActivity)

                    // Buscar dados da conversa (outro pai, filhos, conversation id)
                    loadConversationData(accessToken)

                    goToMain()
                } else {
                    showError("Token não recebido")
                }
            } catch (e: Exception) {
                showError("Erro: ${e.message}")
            }
        }
    }

    /**
     * Após login, busca conversas do usuário para:
     * - Salvar o conversationId real (não hardcoded = 1)
     * - Salvar o nome do outro pai
     * - Salvar nomes dos filhos
     */
    private suspend fun loadConversationData(token: String) {
        try {
            val conversations = RetrofitClient.api.getConversations("Bearer $token")

            if (conversations.isNotEmpty()) {
                val conv = conversations.first() // Pega a primeira conversa

                // Salvar conversation id
                PrefsHelper.saveConversationId(this, conv.id)

                // Encontrar o outro pai (participante que não sou eu)
                val otherParent = conv.participants.firstOrNull { !it.is_me }
                if (otherParent != null) {
                    PrefsHelper.saveOtherParentName(this, otherParent.username)
                }

                // Salvar nomes dos filhos
                if (conv.children.isNotEmpty()) {
                    val childrenNames = conv.children.joinToString(", ") { it.name }
                    PrefsHelper.saveChildrenNames(this, childrenNames)
                }
            }
        } catch (_: Exception) {
            // Falha silenciosa — dados serão carregados depois
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        btnLogin.isEnabled = true
        btnLogin.text = "Entrar"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
