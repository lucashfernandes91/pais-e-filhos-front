package com.example.chatapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AttachmentImageLoader {

    private val imageLoaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun load(imageView: ImageView, url: String) {
        if (url.isBlank()) {
            clear(imageView)
            return
        }
        imageView.setTag(R.id.tag_attachment_image_url, url)

        bitmapCache.get(url)?.let { cachedBitmap ->
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        imageView.setImageDrawable(null)

        // Mídia é servida por endpoint autenticado (B2).
        val token = PrefsHelper.getAuthToken(imageView.context)

        imageLoaderScope.launch {
            val bitmap = fetchBitmap(url, token)
            withContext(Dispatchers.Main) {
                val currentUrl = imageView.getTag(R.id.tag_attachment_image_url) as? String
                if (currentUrl != url) {
                    return@withContext
                }

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageDrawable(null)
                }
            }
        }
    }

    fun clear(imageView: ImageView?) {
        imageView ?: return
        imageView.setTag(R.id.tag_attachment_image_url, null)
        imageView.setImageDrawable(null)
    }

    private fun fetchBitmap(url: String, token: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    if (token.isNotEmpty()) header("Authorization", "Bearer $token")
                }
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }

                val body = response.body ?: return null
                val bytes = body.bytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        MAX_DECODED_DIMENSION
                    )
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { bitmap ->
                    bitmapCache.put(url, bitmap)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun maxCacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxMemoryKb / 8).coerceAtLeast(1024)
    }

    private const val MAX_DECODED_DIMENSION = 2048
}
