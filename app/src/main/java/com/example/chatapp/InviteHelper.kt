package com.example.chatapp

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Gera o código de convite no backend e abre o share sheet.
 * Usado pelo Perfil e pelas Configurações — a regra (código novo invalida
 * o anterior, validade de 7 dias) mora no servidor.
 */
object InviteHelper {

    fun shareInvite(fragment: Fragment) {
        val context = fragment.requireContext()
        val token = PrefsHelper.getAuthToken(context)
        val conversationId = PrefsHelper.getConversationId(context)
        if (token.isEmpty() || conversationId <= PrefsHelper.NO_CONVERSATION_ID) return

        Toast.makeText(context, R.string.profile_invite_generating, Toast.LENGTH_SHORT).show()

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.createInvite(
                    "Bearer $token", mapOf("conversation_id" to conversationId)
                )
                val code = response["code"] as? String ?: return@launch
                val inviteUrl = response["invite_url"] as? String ?: return@launch

                val username = PrefsHelper.getUsername(context)
                val displayName = username.replaceFirstChar { it.uppercase() }
                val inviteText = context.getString(
                    R.string.profile_invite_text, displayName, inviteUrl, code
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.profile_invite_subject))
                    putExtra(Intent.EXTRA_TEXT, inviteText)
                }
                fragment.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.profile_invite_chooser))
                )
            } catch (e: HttpException) {
                val message = ApiErrors.messageFrom(e)
                    ?: context.getString(R.string.profile_invite_error, e.code().toString())
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_invite_error, e.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
