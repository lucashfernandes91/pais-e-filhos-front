package com.example.chatapp

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de Filho com anotações para serialização Gson
 * Usado em operações CRUD via API
 */
data class Child(
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("birth_date")
    val birth_date: String?,

    @SerializedName("conversation")
    val conversation: Int,

    @SerializedName("created_by_name")
    val created_by_name: String? = null,

    @SerializedName("created_at")
    val created_at: String? = null
) : Serializable
