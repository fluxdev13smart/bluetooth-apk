package com.example.btchat.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.BufferedOutputStream
import java.io.OutputStream

/** Saves received files to Downloads/BT Chat (no storage permission needed) and reads files we send. */
object FileStore {

    class Sink(private val ctx: Context, private val uri: Uri, private val out: OutputStream) {
        fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

        fun finish(): String? = try {
            out.close()
            val v = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, v, null, null)
            uri.toString()
        } catch (e: Exception) {
            null
        }

        fun discard() {
            try { out.close() } catch (_: Exception) {}
            try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
    }

    fun create(ctx: Context, name: String, mime: String): Sink? {
        return try {
            val type = if (mime.isBlank() || mime == "application/octet-stream") guessMime(name) else mime
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, type)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BT Chat")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
            val out = ctx.contentResolver.openOutputStream(uri)
            if (out == null) {
                ctx.contentResolver.delete(uri, null, null)
                null
            } else {
                Sink(ctx, uri, BufferedOutputStream(out, 64 * 1024))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun describe(ctx: Context, uri: Uri): PendingFile? {
        var name = "file"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n) ?: name
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (_: Exception) {}
        if (size < 0) {
            size = try {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (_: Exception) {
                -1L
            }
        }
        if (size < 0) return null
        val mime = ctx.contentResolver.getType(uri) ?: guessMime(name)
        return PendingFile(uri, clean(name), size, mime)
    }

    fun clean(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().takeLast(120).ifBlank { "file" }

    fun guessMime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
}
