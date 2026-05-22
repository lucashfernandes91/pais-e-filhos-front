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

    @SerializedName("cpf")
    val cpf: String? = null,

    @SerializedName("rg")
    val rg: String? = null,

    @SerializedName("photo_url")
    val photo_url: String? = null,

    @SerializedName("has_custody")
    val has_custody: Boolean = false,

    @SerializedName("conversation")
    val conversation: Int,

    @SerializedName("created_by_name")
    val created_by_name: String? = null,

    @SerializedName("created_at")
    val created_at: String? = null
) : Serializable
