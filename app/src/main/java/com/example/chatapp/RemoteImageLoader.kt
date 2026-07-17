package com.example.chatapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object RemoteImageLoader {

    private val imageLoaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        imageView: ImageView,
        url: String,
        onLoaded: (() -> Unit)? = null
    ) {
        bitmapCache.get(url)?.let { cachedBitmap ->
            imageView.setImageBitmap(cachedBitmap)
            onLoaded?.invoke()
            return
        }

        // Mídia é servida por endpoint autenticado (B2).
        val token = PrefsHelper.getAuthToken(imageView.context)

        imageLoaderScope.launch {
            val bitmap = fetchBitmap(url, token) ?: return@launch
            withContext(Dispatchers.Main) {
                imageView.setImageBitmap(bitmap)
                onLoaded?.invoke()
            }
        }
    }

    private fun fetchBitmap(url: String, token: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).get().apply {
                if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            }.build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                response.body?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.also { bitmap ->
                    bitmapCache.put(url, bitmap)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun maxCacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxMemoryKb / 8).coerceAtLeast(1024)
    }
}
