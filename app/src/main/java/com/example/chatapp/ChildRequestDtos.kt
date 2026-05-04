package com.example.chatapp

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Request para criar um novo filho
 * Fortemente tipado para evitar problemas com Retrofit/Gson
 */
data class CreateChildRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("conversation_id")
    val conversationId: Int,

    @SerializedName("birth_date")
    val birthDate: String? = null
) : Serializable {
    init {
        require(name.isNotBlank()) { "Nome do filho não pode estar vazio" }
        require(name.length <= 100) { "Nome máximo 100 caracteres" }
        birthDate?.let {
            require(it.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                "Data deve estar no formato YYYY-MM-DD"
            }
        }
    }
}

/**
 * Request para atualizar um filho (PATCH)
 * Todos os campos são opcionais
 */
data class UpdateChildRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("birth_date")
    val birthDate: String? = null
) : Serializable {
    init {
        name?.let {
            require(it.isNotBlank()) { "Nome não pode estar vazio" }
            require(it.length <= 100) { "Nome máximo 100 caracteres" }
        }
        birthDate?.let {
            require(it.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                "Data deve estar no formato YYYY-MM-DD"
            }
        }
    }
}
