package com.example.chatapp

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

/**
 * Handles expired JWT access tokens without starting activities from OkHttp.
 *
 * Starting LoginActivity from an interceptor can race with the Activity lifecycle
 * while the app is already opening the login screen. Temporary refresh failures
 * keep the local session; only a proven-invalid refresh token clears it.
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val correlationId = AppTelemetry.newCorrelationId()
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if (response.code != 401 || originalRequest.header("Authorization") == null) {
            return response
        }

        val url = originalRequest.url.toString()
        if (url.contains("/api/token/")) {
            return response
        }

        val refreshToken = PrefsHelper.getRefreshToken(context)
        if (refreshToken.isEmpty()) {
            AppTelemetry.warning(
                "jwt_refresh_missing",
                correlationId,
                attributes = mapOf("status" to response.code.toString())
            )
            expireLocalSession()
            return response
        }

        val unauthorizedResponse = response.newBuilder()
            .body(ByteArray(0).toResponseBody(response.body?.contentType()))
            .build()
        response.close()

        synchronized(this) {
            val currentToken = PrefsHelper.getAuthToken(context)
            val originalToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")

            if (currentToken != originalToken && currentToken.isNotEmpty()) {
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            when (val refreshResult = performTokenRefresh(chain, refreshToken)) {
                is RefreshResult.Success -> {
                    AppTelemetry.info("jwt_refresh_succeeded", correlationId)
                    PrefsHelper.saveAccessToken(context, refreshResult.accessToken)
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${refreshResult.accessToken}")
                        .build()
                    return chain.proceed(retryRequest)
                }

                RefreshResult.InvalidSession -> {
                    AppTelemetry.warning("jwt_refresh_invalid_session", correlationId)
                    expireLocalSession()
                }
                RefreshResult.RetryableFailure -> {
                    AppTelemetry.warning("jwt_refresh_retryable_failure", correlationId)
                }
            }
            return unauthorizedResponse
        }
    }

    private fun performTokenRefresh(chain: Interceptor.Chain, refreshToken: String): RefreshResult {
        return try {
            val json = JSONObject().apply {
                put("refresh", refreshToken)
            }
            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val refreshRequest = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}api/token/refresh/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val refreshResponse = chain.proceed(refreshRequest)
            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string()
                val responseJson = JSONObject(responseBody ?: "")
                val newAccessToken = responseJson.optString("access", "")
                val newRefreshToken = responseJson.optString("refresh", "")
                if (newRefreshToken.isNotEmpty()) {
                    PrefsHelper.saveRefreshToken(context, newRefreshToken)
                }
                refreshResponse.close()
                if (newAccessToken.isNotEmpty()) {
                    RefreshResult.Success(newAccessToken)
                } else {
                    RefreshResult.RetryableFailure
                }
            } else if (refreshResponse.code == 401 || refreshResponse.code == 403) {
                refreshResponse.close()
                RefreshResult.InvalidSession
            } else {
                refreshResponse.close()
                RefreshResult.RetryableFailure
            }
        } catch (error: Exception) {
            AppTelemetry.warning("jwt_refresh_unavailable", error = error)
            RefreshResult.RetryableFailure
        }
    }

    private fun expireLocalSession() {
        PrefsHelper.clearSession(context)
    }

    private sealed class RefreshResult {
        data class Success(val accessToken: String) : RefreshResult()
        object InvalidSession : RefreshResult()
        object RetryableFailure : RefreshResult()
    }

    companion object {
        private const val TAG = "AuthInterceptor"
    }
}
