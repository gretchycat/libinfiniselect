package io.github.gretchycat.infiniselect

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Serves short-lived JSPF/XML documents created by the selector, without exposing cache paths. */
class SelectionDocumentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String? = record(uri)?.mime

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val item = record(uri) ?: return null
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(columns.map { when (it) {
                OpenableColumns.DISPLAY_NAME -> item.name
                OpenableColumns.SIZE -> item.file.length()
                else -> null
            } })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Selection documents are read-only")
        val item = record(uri) ?: throw FileNotFoundException("This selection document has expired")
        return ParcelFileDescriptor.open(item.file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private data class Record(val file: File, val name: String, val mime: String, val issued: Long)

    private fun record(uri: Uri): Record? {
        val context = context ?: return null
        if (uri.authority != authority(context) || uri.pathSegments.size != 1) return null
        val token = uri.lastPathSegment ?: return null
        if (!token.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) return null
        val prefs = context.getSharedPreferences(PREFS, 0)
        val issued = prefs.getLong("$token.issued", 0L)
        val file = File(context.cacheDir, "$DIRECTORY/$token")
        if (issued == 0L || System.currentTimeMillis() - issued > TTL_MS || !file.isFile) {
            remove(context, token)
            return null
        }
        val name = prefs.getString("$token.name", null) ?: return null
        val mime = prefs.getString("$token.mime", null) ?: return null
        return Record(file, name, mime, issued)
    }

    companion object {
        private const val PREFS = "libinfiniselect.documents"
        private const val DIRECTORY = "infiniselect-documents"
        private const val TTL_MS = 24L * 60L * 60L * 1000L
        private const val AUTHORITY_SUFFIX = ".infiniselect.documents"

        fun create(context: android.content.Context, name: String, mime: String, contents: ByteArray): Uri {
            require(name.isNotBlank() && !name.contains('\u0000')) { "Selection document name is invalid" }
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            cleanup(context, directory)
            val token = UUID.randomUUID().toString()
            val target = File(directory, token)
            val temporary = File(directory, "$token.tmp")
            temporary.outputStream().use { it.write(contents) }
            check(temporary.renameTo(target)) { "Could not publish the selection document" }
            val prefs = context.getSharedPreferences(PREFS, 0)
            val now = System.currentTimeMillis()
            check(prefs.edit().putString("$token.name", name).putString("$token.mime", mime)
                .putLong("$token.issued", now).commit()) { "Could not register the selection document" }
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(token).build()
        }

        private fun cleanup(context: android.content.Context, directory: File) {
            val prefs = context.getSharedPreferences(PREFS, 0)
            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            prefs.all.keys.filter { it.endsWith(".issued") }.forEach { key ->
                val token = key.removeSuffix(".issued")
                if (now - prefs.getLong(key, now) > TTL_MS) {
                    File(directory, token).delete()
                    editor.remove("$token.name").remove("$token.mime").remove(key)
                }
            }
            editor.apply()
        }

        private fun remove(context: android.content.Context, token: String) {
            File(context.cacheDir, "$DIRECTORY/$token").delete()
            context.getSharedPreferences(PREFS, 0).edit().remove("$token.name").remove("$token.mime")
                .remove("$token.issued").apply()
        }

        private fun authority(context: android.content.Context) = context.packageName + AUTHORITY_SUFFIX
    }

    private val authority: String get() = context?.packageName.orEmpty() + AUTHORITY_SUFFIX
}
