package com.example.chatapp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    private var itemCount: Int = 0
    private var exportType: String = TYPE_MESSAGES

    companion object {
        const val TYPE_MESSAGES = "messages"
        const val TYPE_EVENTS = "events"
        private const val ARG_ITEM_COUNT = "item_count"
        private const val ARG_EXPORT_TYPE = "export_type"

        fun newInstance(itemCount: Int, exportType: String): ExportBottomSheet {
            return ExportBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ITEM_COUNT, itemCount)
                    putString(ARG_EXPORT_TYPE, exportType)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemCount = arguments?.getInt(ARG_ITEM_COUNT, 0) ?: 0
        exportType = arguments?.getString(ARG_EXPORT_TYPE) ?: TYPE_MESSAGES
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_export, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivExportIcon = view.findViewById<ImageView>(R.id.ivExportIcon)
        val tvExportTitle = view.findViewById<TextView>(R.id.tvExportTitle)
        val tvExportInfo = view.findViewById<TextView>(R.id.tvExportInfo)
        val tvMessageCount = view.findViewById<TextView>(R.id.tvMessageCount)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btnDownloadPdf)
        val btnShare = view.findViewById<MaterialButton>(R.id.btnSharePdf)
        val progressContainer = view.findViewById<LinearLayout>(R.id.progressContainer)
        val tvProgressText = view.findViewById<TextView>(R.id.tvProgressText)

        if (exportType == TYPE_EVENTS) {
            ivExportIcon.setImageResource(R.drawable.ic_calendar)
            ivExportIcon.contentDescription = getString(R.string.ui_eventos)
            tvExportTitle.setText(R.string.ui_exportar_eventos)
            tvExportInfo.setText(R.string.ui_gera_um_pdf_com_registro_oficial_dos_eventos)
            tvMessageCount.text =
                resources.getQuantityString(R.plurals.export_event_count, itemCount, itemCount)
        } else {
            tvExportTitle.setText(R.string.ui_exportar_mensagens)
            tvExportInfo.setText(R.string.ui_gera_um_pdf_com_registro_oficial_de_todas)
            tvMessageCount.text =
                resources.getQuantityString(R.plurals.export_message_count, itemCount, itemCount)
        }

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
            Toast.makeText(ctx, R.string.export_session_expired, Toast.LENGTH_SHORT).show()
            return
        }

        progressContainer.visibility = View.VISIBLE
        tvProgressText.setText(R.string.export_generating_pdf)
        btnDownload.isEnabled = false
        btnShare.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseBody = RetrofitClient.api.exportConversationPdf(
                    "Bearer $token",
                    conversationId,
                    exportType
                )

                val downloadDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(ctx.filesDir, "downloads").also { it.mkdirs() }

                val typeLabel = if (exportType == TYPE_EVENTS) "Eventos" else "Mensagens"
                val fileName = "CoParent_${typeLabel}_${conversationId}.pdf"
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
                        Toast.makeText(ctx, R.string.export_success, Toast.LENGTH_SHORT).show()
                    }
                    btnDownload.isEnabled = true
                    btnShare.isEnabled = true

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
                        getString(R.string.export_error, e.message.orEmpty()),
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
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_share_chooser)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.export_share_error, e.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
}
