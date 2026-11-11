package io.github.gretchycat.infiniselect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.FileNotFoundException

/** Android-native storage operations. Each returned stream/descriptor belongs to its caller. */
object StorageAccess {
    enum class Support { SUPPORTED, UNSUPPORTED, UNKNOWN }
    data class Capabilities(val readable: Support, val writable: Support, val directory: Boolean,
        val seekable: Support = Support.UNKNOWN, val persistable: Boolean = false)

    fun openRead(context: Context, uri: Uri): java.io.InputStream =
        context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("Provider returned no readable stream")
    fun openWrite(context: Context, uri: Uri): java.io.OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("Provider returned no writable stream")
    /** Random access is provider-dependent; pipes remain pipes and are never copied to a temporary file. */
    fun openDescriptor(context: Context, uri: Uri, writable: Boolean = false): ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, if (writable) "rw" else "r")
            ?: throw FileNotFoundException("Provider returned no descriptor")

    /** A non-destructive seek probe on the descriptor the caller already owns. */
    fun seekable(descriptor: ParcelFileDescriptor): Support = try {
        android.system.Os.lseek(descriptor.fileDescriptor, 0, android.system.OsConstants.SEEK_CUR)
        Support.SUPPORTED
    } catch (error: android.system.ErrnoException) {
        if (error.errno == android.system.OsConstants.ESPIPE) Support.UNSUPPORTED else throw error
    }

    /** Metadata flags describe the provider's promises. UNKNOWN never authorizes a mutation. */
    fun capabilities(context: Context, uri: Uri): Capabilities {
        val document = DocumentsContract.isDocumentUri(context, uri)
        val flags = if (document) context.contentResolver.query(uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getInt(0)
        } else null
        val directory = context.contentResolver.getType(uri) == DocumentsContract.Document.MIME_TYPE_DIR
        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri || (DocumentsContract.isTreeUri(it.uri) && it.uri.authority == uri.authority &&
                runCatching { DocumentsContract.getTreeDocumentId(it.uri) == DocumentsContract.getTreeDocumentId(uri) }.getOrDefault(false))
        }
        val readGranted = context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return Capabilities(if (readGranted) Support.SUPPORTED else Support.UNKNOWN,
            if (flags == null) Support.UNKNOWN else if (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0)
                Support.SUPPORTED else Support.UNSUPPORTED, directory, persistable = persisted)
    }

    /** Request a grantable tree, preferring Infinifolder's standard provider when installed. */
    fun directoryIntent(context: Context, initialDirectory: Uri? = null, writable: Boolean = false): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            if (writable) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val initial = initialDirectory ?: context.packageManager.resolveContentProvider("com.infinifolder.documents", 0)
                ?.let { DocumentsContract.buildDocumentUri(it.authority, "root") }
            initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
    fun selectDirectory(activity: Activity, requestCode: Int, initialDirectory: Uri? = null, writable: Boolean = false) {
        activity.startActivityForResult(directoryIntent(activity, initialDirectory, writable), requestCode)
    }
    /** Persist only flags actually granted by the result. Returns false for temporary relay references. */
    fun persist(context: Context, result: Intent): Boolean {
        val uri = result.data ?: return false
        if (result.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return false
        val flags = result.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return try { context.contentResolver.takePersistableUriPermission(uri, flags); true }
        catch (_: SecurityException) { false }
    }
}
