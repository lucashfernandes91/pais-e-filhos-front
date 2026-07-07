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
        try {
            val resolver = context.contentResolver

            // decodeStream sempre retorna null com inJustDecodeBounds=true; o que importa
            // aqui é popular bounds.outWidth/outHeight, não o valor de retorno.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val largestSide = maxOf(bitmap.width, bitmap.height)
            if (largestSide > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / largestSide
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
                )
                bitmap.recycle()
                bitmap = scaled
            }

            val rotationDegrees = resolver.openInputStream(uri)?.use {
                ExifInterface(it).rotationDegrees
            } ?: 0
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                bitmap = rotated
            }

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            bitmap.recycle()
            output.toByteArray()
        } catch (_: Exception) {
            null
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
