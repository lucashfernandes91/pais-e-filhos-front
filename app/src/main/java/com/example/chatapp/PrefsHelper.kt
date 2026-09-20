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

    // Keys — canonical (use `pref_` prefix for user-visible settings)
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USERNAME = "username"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_CONVERSATION_ID = "conversation_id"
    private const val KEY_PUSH_ENABLED = "pref_push_enabled"
    private const val KEY_EMAIL_NOTIFICATIONS = "pref_email_notifications"
    private const val KEY_EMAIL_BACKUP = "pref_email_backup"
    private const val KEY_NIGHT_MODE = "pref_night_mode"
    private const val KEY_OTHER_PARENT_NAME = "other_parent_name"
    private const val KEY_CHILDREN_NAMES = "children_names"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_PENDING_INVITE_CODE = "pending_invite_code"
    private const val KEY_WELCOME_PENDING = "welcome_pending"

    // Legacy keys kept only for one-shot migration; do not use elsewhere.
    private const val LEGACY_KEY_PUSH_NOTIFICATIONS = "push_notifications"
    private const val LEGACY_KEY_EMAIL_NOTIFICATIONS = "email_notifications"
    private const val KEY_MIGRATION_V2_DONE = "_migration_v2_done"

    const val NO_CONVERSATION_ID = 0

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also(::migrateIfNeeded)

    /**
     * One-shot migration: copia valores das chaves legadas para as novas
     * e remove as antigas. Idempotente via flag [KEY_MIGRATION_V2_DONE].
     *
     * Motivação: até v1, NotificationSettingsFragment escrevia em
     * "pref_push_enabled" enquanto isPushEnabled() lia "push_notifications".
     * Toggles do usuário ficavam invisíveis para o helper.
     */
    private fun migrateIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_MIGRATION_V2_DONE, false)) return

        prefs.edit().apply {
            if (prefs.contains(LEGACY_KEY_PUSH_NOTIFICATIONS)) {
                putBoolean(KEY_PUSH_ENABLED, prefs.getBoolean(LEGACY_KEY_PUSH_NOTIFICATIONS, true))
                remove(LEGACY_KEY_PUSH_NOTIFICATIONS)
            }
            if (prefs.contains(LEGACY_KEY_EMAIL_NOTIFICATIONS)) {
                putBoolean(
                    KEY_EMAIL_NOTIFICATIONS,
                    prefs.getBoolean(LEGACY_KEY_EMAIL_NOTIFICATIONS, false)
                )
                remove(LEGACY_KEY_EMAIL_NOTIFICATIONS)
            }
            putBoolean(KEY_MIGRATION_V2_DONE, true)
        }.apply()
    }

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
            .putInt(KEY_CONVERSATION_ID, NO_CONVERSATION_ID)
            .remove(KEY_OTHER_PARENT_NAME)
            .remove(KEY_CHILDREN_NAMES)
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

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USERNAME)
            .putInt(KEY_CONVERSATION_ID, NO_CONVERSATION_ID)
            .remove(KEY_OTHER_PARENT_NAME)
            .remove(KEY_CHILDREN_NAMES)
            .apply()
    }

    // ── Convite pendente (deep link antes do login) ───────

    fun getPendingInviteCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_INVITE_CODE, null)

    fun savePendingInviteCode(context: Context, code: String) {
        prefs(context).edit()
            .putString(KEY_PENDING_INVITE_CODE, code)
            .apply()
    }

    fun clearPendingInviteCode(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_INVITE_CODE)
            .apply()
    }

    // ── Boas-vindas pós-cadastro (B8) ─────────────────────

    fun isWelcomePending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WELCOME_PENDING, false)

    fun setWelcomePending(context: Context, pending: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_WELCOME_PENDING, pending)
            .apply()
    }

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(context: Context, complete: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, complete)
            .commit()
    }

    // ── Conversation ─────────────────────────────────────

    fun getConversationId(context: Context): Int =
        prefs(context).getInt(KEY_CONVERSATION_ID, NO_CONVERSATION_ID)

    fun hasConversationId(context: Context): Boolean =
        getConversationId(context) > NO_CONVERSATION_ID

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
        prefs(context).getBoolean(KEY_PUSH_ENABLED, true)

    fun setPushEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PUSH_ENABLED, enabled)
            .apply()
    }

    fun isEmailEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMAIL_NOTIFICATIONS, false)

    fun setEmailEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_EMAIL_NOTIFICATIONS, enabled)
            .apply()
    }

    fun isEmailBackupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EMAIL_BACKUP, true)

    fun setEmailBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_EMAIL_BACKUP, enabled)
            .apply()
    }

    fun isNightModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NIGHT_MODE, false)

    fun setNightModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_NIGHT_MODE, enabled)
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
