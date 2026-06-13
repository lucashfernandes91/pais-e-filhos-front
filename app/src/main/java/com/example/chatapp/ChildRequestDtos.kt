package com.example.chatapp

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CreateChildRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("conversation_id")
    val conversationId: Int,

    @SerializedName("birth_date")
    val birthDate: String,

    @SerializedName("cpf")
    val cpf: String? = null,

    @SerializedName("rg")
    val rg: String? = null,

    @SerializedName("has_custody")
    val hasCustody: Boolean = false
) : Serializable {
    init {
        require(name.isNotBlank()) { "Nome do filho não pode estar vazio" }
        require(name.length <= 100) { "Nome máximo 100 caracteres" }
        require(birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            "Data de nascimento deve estar no formato YYYY-MM-DD"
        }
    }
}

data class UpdateChildRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("birth_date")
    val birthDate: String? = null,

    @SerializedName("cpf")
    val cpf: String? = null,

    @SerializedName("rg")
    val rg: String? = null,

    @SerializedName("has_custody")
    val hasCustody: Boolean? = null
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
