package com.example.chatapp

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Child(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("birth_date")
    val birth_date: String?,

    @SerializedName("photo_url")
    val photo_url: String? = null,

    @SerializedName("has_custody")
    val has_custody: Boolean = false,

    @SerializedName("custody_holder_name")
    val custody_holder_name: String? = null,

    @SerializedName("conversation")
    val conversation: Int,

    @SerializedName("created_by_name")
    val created_by_name: String? = null,

    @SerializedName("created_at")
    val created_at: String? = null
) : Serializable {
    fun isUnderCustodyOf(username: String): Boolean =
        has_custody &&
            (custody_holder_name ?: created_by_name)
                ?.trim()
                ?.equals(username.trim(), ignoreCase = true) == true
}
