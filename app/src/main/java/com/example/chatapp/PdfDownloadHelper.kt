package com.example.chatapp

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Helper para download de PDF de conversas.
 *
 * Recebe um [CoroutineScope] externo (ex: lifecycleScope do Fragment/Activity)
 * para garantir cancelamento automático quando o componente é destruído.
 */
object PdfDownloadHelper {

    private const val TAG = "PdfDownloadHelper"

    fun downloadConversationPdf(
        context: Context,
        authToken: String,
        conversationId: Int,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting PDF download for conversation $conversationId")

                // Chama API
                val responseBody = RetrofitClient.api.exportConversationPdf(
                    "Bearer $authToken",
                    conversationId
                )

                // Salva em Downloads
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads").also { it.mkdirs() }

                val fileName = "CoParent_Conversation_${conversationId}.pdf"
                val pdfFile = File(downloadDir, fileName)

                // Escreve no arquivo
                responseBody.byteStream().use { input ->
                    FileOutputStream(pdfFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d(TAG, "PDF saved: ${pdfFile.absolutePath}")

                // Notifica usuário na Main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "PDF salvo em Downloads",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading PDF: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Erro ao baixar PDF: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
