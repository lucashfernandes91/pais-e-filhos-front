package com.example.chatapp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Avisa o servidor no logout: blacklista o refresh token e remove o token
 * do aparelho (para de receber push). Fire-and-forget — o logout local
 * não espera nem depende da rede.
 */
object LogoutHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notifyServerLogout(context: Context) {
        val appContext = context.applicationContext
        val access = PrefsHelper.getAuthToken(appContext)
        if (access.isEmpty()) return

        val body = buildMap {
            PrefsHelper.getRefreshToken(appContext)
                .takeIf { it.isNotEmpty() }?.let { put("refresh", it) }
            PrefsHelper.getFcmToken(appContext)
                ?.takeIf { it.isNotEmpty() }?.let { put("device_token", it) }
        }

        scope.launch {
            try {
                RetrofitClient.api.logout("Bearer $access", body)
            } catch (_: Exception) {
                // Melhor esforço: o refresh expira sozinho em até 7 dias.
            }
        }
    }
}
