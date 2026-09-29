package com.example.chatapp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class AttachmentInfo(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extension: String
)

object AttachmentPolicy {
    const val MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L
    const val MAX_MESSAGE_LENGTH = 2000

    private val allowedMimeTypes = mapOf(
        "jpg" to setOf("image/jpeg"),
        "jpeg" to setOf("image/jpeg"),
        "png" to setOf("image/png"),
        "webp" to setOf("image/webp"),
        "pdf" to setOf("application/pdf")
    )
    private val blockedExtensions = setOf(
        "svg", "html", "htm", "js", "mjs", "exe", "dll", "bat", "cmd", "sh", "zip"
    )

    fun inspect(context: Context, uri: Uri): Result<AttachmentInfo> {
        val resolver = context.contentResolver
        var fileName = "attachment"
        var sizeBytes = -1L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fileParts = fileName.split('.').dropLast(1).map { it.lowercase() }
        if (extension.isEmpty() || fileParts.any { it in blockedExtensions }) {
            return Result.failure(IllegalArgumentException("unsupported_type"))
        }

        val allowedMimes = allowedMimeTypes[extension]
            ?: return Result.failure(IllegalArgumentException("unsupported_type"))
        val detectedMime = resolver.getType(uri)?.lowercase()
        if (detectedMime != null && detectedMime != "application/octet-stream" && detectedMime !in allowedMimes) {
            return Result.failure(IllegalArgumentException("unsupported_type"))
        }

        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            return Result.failure(IllegalArgumentException("file_too_large"))
        }

        return Result.success(
            AttachmentInfo(
                fileName = fileName,
                mimeType = allowedMimes.first(),
                sizeBytes = sizeBytes,
                extension = extension
            )
        )
    }
}
