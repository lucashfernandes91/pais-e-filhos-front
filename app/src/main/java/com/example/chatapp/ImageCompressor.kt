package com.example.chatapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Comprime fotos de anexo antes do upload, no mesmo espírito do WhatsApp:
 * redimensiona pro lado maior caber em MAX_DIMENSION e reencoda como JPEG,
 * evitando estourar o limite de upload do backend e economizando banda.
 * Documentos (PDF etc.) não passam por aqui.
 */
object ImageCompressor {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    /** Retorna os bytes da imagem comprimida, ou null se não foi possível decodificar. */
    suspend fun compress(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var resized: Bitmap? = null
        try {
            val resolver = context.contentResolver

            // decodeStream sempre retorna null com inJustDecodeBounds=true; o que importa
            // aqui é popular bounds.outWidth/outHeight, não o valor de retorno.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val rotationDegrees = resolver.openInputStream(uri)?.use {
                ExifInterface(it).rotationDegrees
            } ?: return@withContext null
            val orientationMatrix = Matrix().apply {
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            }
            oriented = Bitmap.createBitmap(
                decoded!!,
                0,
                0,
                decoded!!.width,
                decoded!!.height,
                orientationMatrix,
                true
            ).copy(Bitmap.Config.ARGB_8888, false)
            if (oriented !== decoded) decoded?.recycle()
            decoded = null

            val largestSide = maxOf(oriented!!.width, oriented!!.height)
            if (largestSide > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / largestSide
                resized = Bitmap.createScaledBitmap(
                    oriented!!,
                    (oriented!!.width * scale).toInt(),
                    (oriented!!.height * scale).toInt(),
                    true
                )
                oriented?.recycle()
                oriented = resized
                resized = null
            }

            val output = ByteArrayOutputStream()
            check(oriented!!.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "image_encoding_failed"
            }
            output.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            resized?.recycle()
            oriented?.recycle()
            decoded?.recycle()
        }
    }

    /** Maior potência de 2 que ainda mantém a imagem decodificada acima de reqSize (downsample barato antes do resize exato). */
    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / inSampleSize >= reqSize && halfHeight / inSampleSize >= reqSize) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
