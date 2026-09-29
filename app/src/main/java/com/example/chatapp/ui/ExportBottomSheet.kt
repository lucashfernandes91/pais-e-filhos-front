package com.example.chatapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ExportBottomSheet private constructor(
    private val host: Fragment,
    private val itemCount: Int,
    private val exportType: String
) {

    companion object {
        const val TYPE_MESSAGES = "messages"
        const val TYPE_EVENTS = "events"

        fun show(host: Fragment, itemCount: Int, exportType: String) {
            ExportBottomSheet(host, itemCount, exportType).show()
        }
    }

    private lateinit var dialog: BottomSheetDialog

    private fun show() {
        val ctx = host.requireContext()
        val view = host.layoutInflater.inflate(R.layout.bottom_sheet_export, null, false)
        dialog = BottomSheetDialog(ctx, R.style.ExportBottomSheetDialogTheme).apply {
            setContentView(view)
        }

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
            ivExportIcon.contentDescription = ctx.getString(R.string.ui_eventos)
            tvExportTitle.setText(R.string.ui_exportar_eventos)
            tvExportInfo.setText(R.string.ui_gera_um_pdf_com_registro_oficial_dos_eventos)
            tvMessageCount.text =
                ctx.resources.getQuantityString(R.plurals.export_event_count, itemCount, itemCount)
        } else {
            tvExportTitle.setText(R.string.ui_exportar_mensagens)
            tvExportInfo.setText(R.string.ui_gera_um_pdf_com_registro_oficial_de_todas)
            tvMessageCount.text =
                ctx.resources.getQuantityString(R.plurals.export_message_count, itemCount, itemCount)
        }

        btnDownload.setOnClickListener {
            exportPdf(view, progressContainer, tvProgressText, btnDownload, btnShare, share = false)
        }
        btnShare.setOnClickListener {
            exportPdf(view, progressContainer, tvProgressText, btnDownload, btnShare, share = true)
        }

        dialog.show()
    }

    private fun exportPdf(
        view: View,
        progressContainer: LinearLayout,
        tvProgressText: TextView,
        btnDownload: MaterialButton,
        btnShare: MaterialButton,
        share: Boolean
    ) {
        val ctx = host.requireContext()
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

        host.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseBody = RetrofitClient.api.exportConversationPdf(
                    "Bearer $token",
                    conversationId,
                    exportType
                )

                val typeLabel = if (exportType == TYPE_EVENTS) "Eventos" else "Mensagens"
                val fileName = "CoParent_${typeLabel}_${conversationId}.pdf"
                val pdfUri = responseBody.byteStream().use { input ->
                    savePdfToDownloads(ctx, input, fileName)
                }

                withContext(Dispatchers.Main) {
                    if (share) {
                        sharePdfFile(ctx, pdfUri)
                    } else {
                        tvProgressText.setText(R.string.export_pdf_saved)
                        Toast.makeText(ctx, R.string.export_success, Toast.LENGTH_SHORT).show()
                        view.postDelayed({
                            if (dialog.isShowing) dialog.dismiss()
                        }, 1500)
                    }
                    btnDownload.isEnabled = true
                    btnShare.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressContainer.visibility = View.GONE
                    btnDownload.isEnabled = true
                    btnShare.isEnabled = true
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.export_error, e.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun savePdfToDownloads(ctx: Context, input: InputStream, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CoParent")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Não foi possível criar o arquivo em Downloads")
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                } ?: error("Não foi possível gravar o arquivo em Downloads")
            } catch (e: Exception) {
                ctx.contentResolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val coparentDir = File(downloadDir, "CoParent").also { it.mkdirs() }
            val pdfFile = File(coparentDir, fileName)
            FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
            Uri.fromFile(pdfFile)
        }
    }

    private fun sharePdfFile(ctx: Context, uri: Uri) {
        try {
            val shareUri = if (uri.scheme == "file") {
                FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    File(requireNotNull(uri.path))
                )
            } else {
                uri
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.export_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            host.startActivity(
                Intent.createChooser(shareIntent, ctx.getString(R.string.export_share_chooser))
            )
        } catch (e: Exception) {
            Toast.makeText(
                ctx,
                ctx.getString(R.string.export_share_error, e.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
