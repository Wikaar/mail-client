package com.mailtabs.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Вложение создаваемого письма. Содержимое всегда лежит локальной копией в [path]
 * (внутренняя папка приложения), поэтому не зависит от временного доступа к content://-URI
 * и переживает сохранение черновика.
 */
data class ComposeAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val path: String,
) {
    val file: File get() = File(path)
}

@Singleton
class ComposeAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.filesDir, "compose_attachments").apply { mkdirs() }

    /** Копирует файл, выбранный через Storage Access Framework, во внутреннее хранилище. */
    suspend fun import(uri: Uri): ComposeAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        val fileName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: mimeFromName(fileName)
        val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open $uri")
        input.use { save(fileName, mime, it) }
    }

    /** Сохраняет поток (например, часть исходного письма при пересылке) как вложение. */
    suspend fun save(fileName: String, mimeType: String, input: InputStream): ComposeAttachment = withContext(Dispatchers.IO) {
        cleanupStale()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val target = File(dir, "data")
        target.outputStream().use { input.copyTo(it) }
        ComposeAttachment(
            id = id,
            name = fileName,
            mimeType = mimeType.substringBefore(';').trim().lowercase().ifBlank { mimeFromName(fileName) },
            size = target.length(),
            path = target.absolutePath,
        )
    }

    fun delete(a: ComposeAttachment) {
        runCatching { a.file.parentFile?.takeIf { it.parentFile == root }?.deleteRecursively() }
    }

    fun deleteAll(list: List<ComposeAttachment>) = list.forEach(::delete)

    /** Удаляет брошенные копии старше недели (экран закрыли без отправки и черновика). */
    private fun cleanupStale() {
        val limit = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        root.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
    }

    companion object {
        fun mimeFromName(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
