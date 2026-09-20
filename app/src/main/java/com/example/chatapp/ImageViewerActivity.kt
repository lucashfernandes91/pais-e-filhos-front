package com.example.chatapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Tela cheia com zoom pra visualizar um anexo de imagem do chat em tamanho maior, com opção de baixar. */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_IMAGE_URL = "extra_image_url"

        fun start(context: Context, imageUrl: String) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java)
                    .putExtra(EXTRA_IMAGE_URL, imageUrl)
            )
        }
    }

    private lateinit var ivFullImage: ZoomableImageView

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            downloadImage()
        } else {
            Toast.makeText(this, R.string.image_download_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)
        window.statusBarColor = Color.BLACK

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        if (imageUrl.isNullOrEmpty()) {
            finish()
            return
        }

        ivFullImage = findViewById(R.id.ivFullImage)
        ivFullImage.onTap = { finish() }
        AttachmentImageLoader.load(ivFullImage, imageUrl)

        findViewById<View>(R.id.btnCloseViewer).setOnClickListener { finish() }
        findViewById<View>(R.id.btnDownloadViewer).setOnClickListener { requestDownload() }
    }

    private fun requestDownload() {
        // A partir do Android 10 (Q), gravar em Pictures via MediaStore não exige permissão.
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (needsLegacyPermission &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        downloadImage()
    }

    private fun downloadImage() {
        val bitmap = (ivFullImage.drawable as? BitmapDrawable)?.bitmap ?: return

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { saveBitmapToGallery(bitmap) }
            Toast.makeText(
                this@ImageViewerActivity,
                if (saved) R.string.image_download_success else R.string.image_download_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        val filename = "CoParent_${System.currentTimeMillis()}.jpg"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CoParent")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: return false
            } else {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "CoParent"
                )
                if (!picturesDir.exists()) picturesDir.mkdirs()
                val file = File(picturesDir, filename)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
