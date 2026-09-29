package com.example.chatapp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal object MessageJson {
    private val gson = Gson()
    private val readByType = object : TypeToken<List<MessageRead>>() {}.type

    fun encodeReadBy(value: List<MessageRead>): String = gson.toJson(value)

    fun decodeReadBy(value: String): List<MessageRead> =
        runCatching { gson.fromJson<List<MessageRead>>(value, readByType) }.getOrDefault(emptyList())
}
