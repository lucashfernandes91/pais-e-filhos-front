package com.example.chatapp

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.UUID

/**
 * Centralizes production-safe diagnostics. Correlation IDs are random and never
 * derived from usernames, tokens, message content, or server identifiers.
 */
object AppTelemetry {
    private const val TAG = "CoParentTelemetry"
    private val crashlytics = FirebaseCrashlytics.getInstance()

    fun newCorrelationId(): String = UUID.randomUUID().toString()

    fun debug(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun info(event: String, correlationId: String? = null, attributes: Map<String, String> = emptyMap()) {
        record(event, correlationId, attributes)
        if (BuildConfig.DEBUG) Log.i(TAG, format(event, correlationId, attributes))
    }

    fun warning(
        event: String,
        correlationId: String? = null,
        error: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        record(event, correlationId, attributes)
        if (BuildConfig.DEBUG) {
            Log.w(TAG, format(event, correlationId, attributes), error)
        }
    }

    fun error(
        event: String,
        correlationId: String? = null,
        error: Throwable? = null,
        attributes: Map<String, String> = emptyMap()
    ) {
        record(event, correlationId, attributes)
        if (BuildConfig.DEBUG) {
            Log.e(TAG, format(event, correlationId, attributes), error)
        }
        if (error != null) {
            crashlytics.recordException(error)
        }
    }

    private fun record(
        event: String,
        correlationId: String?,
        attributes: Map<String, String>
    ) {
        crashlytics.log(format(event, correlationId, attributes))
        crashlytics.setCustomKey("last_event", event)
        if (!correlationId.isNullOrBlank()) {
            crashlytics.setCustomKey("correlation_id", correlationId)
        }
        attributes.forEach { (key, value) ->
            crashlytics.setCustomKey(key.take(40), value.take(100))
        }
    }

    private fun format(
        event: String,
        correlationId: String?,
        attributes: Map<String, String>
    ): String {
        val details = attributes.entries.joinToString(",") { "${it.key}=${it.value}" }
        return "event=$event correlation_id=${correlationId ?: "none"} $details"
    }
}
