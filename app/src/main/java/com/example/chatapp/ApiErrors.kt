package com.example.chatapp

import org.json.JSONObject
import retrofit2.HttpException

/**
 * Extrai a mensagem legível do envelope de erro da API:
 * {"error": {"code": "...", "message": "..."}}
 */
object ApiErrors {

    fun messageFrom(exception: HttpException): String? {
        return try {
            exception.response()?.errorBody()?.string()?.let { body ->
                JSONObject(body)
                    .optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
