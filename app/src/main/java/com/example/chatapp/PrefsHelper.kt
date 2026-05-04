package com.example.chatapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Centraliza acesso a SharedPreferences do app.
 *
 * Todas as leituras/escritas de preferências passam por aqui,
 * evitando mismatch de namespace e chaves espalhadas pelo código.
 *
 * Uso:
 *   val token = PrefsHelper.getAuthToken(context)
 *   val convId = PrefsHelper.getConversationId(context)
 */
object PrefsHelper {

    private const val PREFS_NAME = "coparent"

    // Keys
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USERNAME = "username"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_CONVERSATION_ID = "conversation_id"
    private const val KEY_PUSH_NOTIFICATIONS = "push_notifications"
    private const val KEY_EMAIL_NOTIFICATIONS = "email_notifications"
    private const val KEY_OTHER_PARENT_NAME = "other_parent_name"
    private const val KEY_CHILDREN_NAMES = "children_names"

    // Default conversation ID enquanto o app suporta apenas uma conversa
    private const val DEFAULT_CONVERSATION_ID = 1

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Auth ──────────────────────────────────────────────

    fun getAuthToken(context: Context): String =
        prefs(context).getString(KEY_AUTH_TOKEN, "") ?: ""

    fun getRefreshToken(context: Context): String =
        prefs(context).getString(KEY_REFRESH_TOKEN, "") ?: ""

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "") ?: ""

    fun saveCredentials(context: Context, token: String, refreshToken: String, username: String) {
        prefs(context).edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun saveAccessToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun saveRefreshToken(context: Context, refreshToken: String) {
        prefs(context).edit()
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ── Conversation ─────────────────────────────────────

    fun getConversationId(context: Context): Int =
        prefs(context).getInt(KEY_CONVERSATION_ID, DEFAULT_CONVERSATION_ID)

    fun saveConversationId(context: Context, conversationId: Int) {
        prefs(context).edit()
            .putInt(KEY_CONVERSATION_ID, conversationId)
            .apply()
    }

    // ── FCM ──────────────────────────────────────────────

    fun getFcmToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)

    fun saveFcmToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    // ── Settings ─────────────────────────────────────────

    fun isPushEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PUSH_NOTIFICATIONS, true)

    fun setPushEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PUSH_NOTIFICATIONS, enabled)
            .apply()
    }

    fun isEmailEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMAIL_NOTIFICATIONS, false)

    fun setEmailEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_EMAIL_NOTIFICATIONS, enabled)
            .apply()
    }

    // ── Other Parent ─────────────────────────────────────

    fun getOtherParentName(context: Context): String =
        prefs(context).getString(KEY_OTHER_PARENT_NAME, "") ?: ""

    fun saveOtherParentName(context: Context, name: String) {
        prefs(context).edit()
            .putString(KEY_OTHER_PARENT_NAME, name)
            .apply()
    }

    fun getChildrenNames(context: Context): String =
        prefs(context).getString(KEY_CHILDREN_NAMES, "") ?: ""

    fun saveChildrenNames(context: Context, names: String) {
        prefs(context).edit()
            .putString(KEY_CHILDREN_NAMES, names)
            .apply()
    }
}
