package com.example.chatapp

import android.content.Context
import android.content.Intent
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Interceptor que detecta 401 e tenta refresh automático do token JWT.
 *
 * Fluxo:
 * 1. Request original retorna 401
 * 2. Lê refresh token do SharedPreferences
 * 3. Chama POST /api/token/refresh/ com o refresh token
 * 4. Se sucesso: salva novo access token e retenta a request original
 * 5. Se falha: redireciona para LoginActivity
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Se não é 401 ou a request não tem Authorization header, retorna normal
        if (response.code != 401 || originalRequest.header("Authorization") == null) {
            return response
        }

        // Não tentar refresh em endpoints de auth
        val url = originalRequest.url.toString()
        if (url.contains("/api/token/")) {
            return response
        }

        // Tentar refresh
        val refreshToken = PrefsHelper.getRefreshToken(context)
        if (refreshToken.isEmpty()) {
            redirectToLogin()
            return response
        }

        // Fechar o body da response original antes de tentar refresh
        response.close()

        synchronized(this) {
            // Verificar se outro thread já fez o refresh
            val currentToken = PrefsHelper.getAuthToken(context)
            val originalToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")

            if (currentToken != originalToken && currentToken.isNotEmpty()) {
                // Outro thread já fez refresh, retentar com token atualizado
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            // Fazer refresh
            val newToken = performTokenRefresh(chain, refreshToken)

            if (newToken != null) {
                // Salvar novo token
                PrefsHelper.saveAccessToken(context, newToken)

                // Retentar request original com novo token
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                // Refresh falhou — sessão expirada
                redirectToLogin()
                return chain.proceed(originalRequest)
            }
        }
    }

    private fun performTokenRefresh(chain: Interceptor.Chain, refreshToken: String): String? {
        return try {
            val json = JSONObject().apply {
                put("refresh", refreshToken)
            }
            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val refreshRequest = Request.Builder()
                .url("http://10.0.2.2:8000/api/token/refresh/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val refreshResponse = chain.proceed(refreshRequest)

            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string()
                val responseJson = JSONObject(responseBody ?: "")
                val newAccessToken = responseJson.optString("access", "")

                // Se o server rotaciona refresh tokens, salvar o novo
                val newRefreshToken = responseJson.optString("refresh", "")
                if (newRefreshToken.isNotEmpty()) {
                    PrefsHelper.saveRefreshToken(context, newRefreshToken)
                }

                refreshResponse.close()
                if (newAccessToken.isNotEmpty()) newAccessToken else null
            } else {
                refreshResponse.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun redirectToLogin() {
        PrefsHelper.clearAll(context)
        try {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Se não conseguir iniciar activity (edge case), ignora
        }
    }
}
