package com.example.chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var messageCount: Int = 0

    companion object {
        private const val ARG_MESSAGE_COUNT = "message_count"

        fun newInstance(messageCount: Int): ExportBottomSheet {
            return ExportBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MESSAGE_COUNT, messageCount)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageCount = arguments?.getInt(ARG_MESSAGE_COUNT, 0) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_export, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMessageCount = view.findViewById<TextView>(R.id.tvMessageCount)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btnDownloadPdf)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnSharePdf)
        val progressContainer = view.findViewById<LinearLayout>(R.id.progressContainer)
        val tvProgressText = view.findViewById<TextView>(R.id.tvProgressText)

        tvMessageCount.text =
            resources.getQuantityString(R.plurals.export_message_count, messageCount, messageCount)

        btnDownload.setOnClickListener {
            exportPdf(progressContainer, tvProgressText, btnDownload, btnShare, share = false)
        }

        btnShare.setOnClickListener {
            exportPdf(progressContainer, tvProgressText, btnDownload, btnShare, share = true)
        }
    }

    private fun exportPdf(
        progressContainer: LinearLayout,
        tvProgressText: TextView,
        btnDownload: MaterialButton,
        btnShare: MaterialButton,
        share: Boolean
    ) {
        val ctx = requireContext()
        val token = PrefsHelper.getAuthToken(ctx)
        val conversationId = PrefsHelper.getConversationId(ctx)

        if (token.isEmpty()) {
            Toast.makeText(ctx, "Sessão expirada. Faça login novamente", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress
        progressContainer.visibility = View.VISIBLE
        tvProgressText.setText(R.string.export_generating_pdf)
        btnDownload.isEnabled = false
        btnShare.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseBody = RetrofitClient.api.exportConversationPdf(
                    "Bearer $token",
                    conversationId
                )

                val downloadDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(ctx.filesDir, "downloads").also { it.mkdirs() }

                val fileName = "CoParent_Conversa_${conversationId}.pdf"
                val pdfFile = File(downloadDir, fileName)

                responseBody.byteStream().use { input ->
                    FileOutputStream(pdfFile).use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (share) {
                        sharePdfFile(pdfFile)
                    } else {
                        tvProgressText.setText(R.string.export_pdf_saved)
                        Toast.makeText(ctx, "PDF salvo com sucesso", Toast.LENGTH_SHORT).show()
                    }
                    btnDownload.isEnabled = true
                    btnShare.isEnabled = true

                    // Auto-dismiss after short delay if download only
                    if (!share) {
                        view?.postDelayed({ dismissAllowingStateLoss() }, 1500)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressContainer.visibility = View.GONE
                    btnDownload.isEnabled = true
                    btnShare.isEnabled = true
                    Toast.makeText(
                        ctx,
                        "Erro ao gerar PDF: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sharePdfFile(file: File) {
        try {
            val ctx = requireContext()
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CoParent - Registro de Conversa")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Compartilhar PDF"))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Erro ao compartilhar: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
}
