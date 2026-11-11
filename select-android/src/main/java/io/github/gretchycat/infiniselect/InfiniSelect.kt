package io.github.gretchycat.infiniselect

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.os.ResultReceiver
import android.database.Cursor
import android.provider.OpenableColumns

/** Request parameters for one viewer's file-open action. */
data class OpenRequest(
    val clientId: String,
    val displayName: String,
    val mimeTypes: List<String> = listOf("*/*"),
    val allowMultiple: Boolean = false,
    val initialDirectory: Uri? = null,
    /** Filename extensions accepted by the requesting viewer, without leading dots. */
    val supportedExtensions: List<String> = emptyList(),
    /** Hide unsupported leaf files in sources that can filter their listing. */
    val hideUnsupported: Boolean = false,
    /** Restore the source listing's vertical scroll position when reopening it. */
    val initialScrollY: Int = 0,
    /** Package a multi-file selection as JSPF, retaining the individual content URIs. */
    val multipleSelectionDelivery: MultipleSelectionDelivery = MultipleSelectionDelivery.ITEM_URIS,
    /** Optionally return a companion XML document with the selected-item metadata. */
    val metadataFormat: SelectionMetadataFormat = SelectionMetadataFormat.NONE,
    /** Let InfiniFolder turn a folder's nested audio files into one playlist. */
    val allowRecursiveAudioDirectories: Boolean = false,
    /** Receives nonterminal progress while InfiniFolder gathers a recursive selection. */
    val progressReceiver: ResultReceiver? = null,
    /** Request an editable Android document instead of a temporary read relay. */
    val writable: Boolean = false,
) {
    init {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "clientId must be a short stable identifier" }
        require(mimeTypes.isNotEmpty()) { "At least one MIME type is required" }
        require(!allowRecursiveAudioDirectories || allowMultiple) { "Recursive audio selection requires multiple selection" }
    }

    internal fun toBundle() = Bundle().apply {
        putString(FileSelectionContract.EXTRA_CLIENT_ID, clientId)
        putString(FileSelectionContract.EXTRA_DISPLAY_NAME, displayName)
        putStringArrayList(FileSelectionContract.EXTRA_MIME_TYPES, ArrayList(mimeTypes))
        putBoolean("io.github.gretchycat.infiniselect.WRITABLE", writable)
        putBoolean(FileSelectionContract.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        putStringArrayList(FileSelectionContract.EXTRA_SUPPORTED_EXTENSIONS, ArrayList(supportedExtensions))
        putBoolean(FileSelectionContract.EXTRA_HIDE_UNSUPPORTED, hideUnsupported)
        putInt(FileSelectionContract.EXTRA_INITIAL_SCROLL_Y, initialScrollY.coerceAtLeast(0))
        putString(FileSelectionContract.EXTRA_MULTIPLE_DELIVERY, multipleSelectionDelivery.name)
        putString(FileSelectionContract.EXTRA_METADATA_FORMAT, metadataFormat.name)
        putBoolean(FileSelectionContract.EXTRA_ALLOW_RECURSIVE_AUDIO_DIRECTORIES, allowRecursiveAudioDirectories)
        progressReceiver?.let { putParcelable(FileSelectionContract.EXTRA_PROGRESS_RECEIVER, it) }
        initialDirectory?.let { putParcelable(FileSelectionContract.EXTRA_INITIAL_DIRECTORY, it) }
    }
}

/** A document to write after the user chooses a destination in InfiniFolder or SAF. */
data class SaveRequest(
    val clientId: String,
    val displayName: String,
    val fileName: String,
    val mimeType: String,
    val sourceUri: Uri,
    val sourceSizeBytes: Long? = null,
    val initialDirectory: Uri? = null,
) {
    init {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "clientId must be a short stable identifier" }
        require(fileName.isNotBlank() && fileName == fileName.substringAfterLast('/') &&
            fileName !in setOf(".", "..") && !fileName.contains('\\') && !fileName.contains('\u0000')) {
            "fileName must be a safe leaf name"
        }
        require(mimeType.isNotBlank()) { "mimeType is required" }
        require(sourceSizeBytes == null || sourceSizeBytes >= 0L) { "sourceSizeBytes must be non-negative" }
    }

    internal fun toIntent(context: Context) = Intent(context, FileSelectActivity::class.java)
        .setAction(FileSelectActivity.ACTION_SAVE)
        .putExtra(FileSelectionContract.EXTRA_CLIENT_ID, clientId)
        .putExtra(FileSelectionContract.EXTRA_DISPLAY_NAME, displayName)
        .putExtra(FileSelectionContract.EXTRA_SAVE_MODE, true)
        .putExtra(FileSelectionContract.EXTRA_SAVE_FILE_NAME, fileName)
        .putExtra(FileSelectionContract.EXTRA_SAVE_MIME_TYPE, mimeType)
        .putExtra(FileSelectionContract.EXTRA_SAVE_SOURCE_URI, sourceUri)
        .putExtra(FileSelectionContract.EXTRA_SAVE_SOURCE_SIZE, sourceSizeBytes ?: -1L)
        .apply { initialDirectory?.let { putExtra(FileSelectionContract.EXTRA_INITIAL_DIRECTORY, it) } }
}

enum class MultipleSelectionDelivery { ITEM_URIS, JSPF }
enum class SelectionMetadataFormat { NONE, XML }

data class SelectionItem(
    val uri: Uri,
    val name: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long? = null,
    val directoryUri: Uri? = null,
    val directoryName: String? = null,
    /** Optional read-granted URI for a sibling directory cover image. */
    val directoryCoverUri: Uri? = null,
    /** True when recursive selection checked the directory for an image URI. */
    val directoryArtworkMetadataResolved: Boolean = false,
)

data class SelectionResult(
    val items: List<SelectionItem>,
    val playlistUri: Uri? = null,
    val metadataUri: Uri? = null,
) {
    val primaryUri: Uri? get() = playlistUri ?: items.firstOrNull()?.uri
}

/** Launches the reusable file selection flow from an Activity. */
object InfiniSelect {
    private const val PREFERENCES = "libinfiniselect.defaults"
    private const val DIRECTORY_PREFIX = "default_directory."

    fun open(activity: Activity, request: OpenRequest, requestCode: Int) {
        val effective = if (request.initialDirectory == null) {
            request.copy(initialDirectory = defaultDirectory(activity, request.clientId))
        } else request
        activity.startActivityForResult(
            Intent(activity, FileSelectActivity::class.java).putExtras(effective.toBundle()), requestCode
        )
    }

    /** Ask InfiniFolder to choose and write into a storage-provider location, falling back to SAF. */
    fun save(activity: Activity, request: SaveRequest, requestCode: Int) {
        val effective = if (request.initialDirectory == null) {
            request.copy(initialDirectory = defaultDirectory(activity, request.clientId))
        } else request
        activity.startActivityForResult(effective.toIntent(activity), requestCode)
    }

    /** Publish bytes as a short-lived readable source for [save]. */
    fun createDocument(context: Context, name: String, mimeType: String, contents: ByteArray): Uri =
        SelectionDocumentProvider.create(context, name, mimeType, contents)

    fun defaultDirectory(context: Context, clientId: String): Uri? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(DIRECTORY_PREFIX + clientId, null)?.let(Uri::parse)

    fun setDefaultDirectory(context: Context, clientId: String, uri: Uri?) {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "clientId must be a short stable identifier" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            if (uri == null) remove(DIRECTORY_PREFIX + clientId)
            else putString(DIRECTORY_PREFIX + clientId, uri.toString())
        }.apply()
    }

    /** Read the normalized single- or multi-item result returned by [FileSelectActivity]. */
    fun readResult(context: Context, intent: Intent): SelectionResult {
        val uris = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_URIS)
            ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .orEmpty()
        val names = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_NAMES).orEmpty()
        val mimes = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_MIMES).orEmpty()
        val sizes = intent.getLongArrayExtra(FileSelectionContract.EXTRA_RESULT_SIZES)
        val directoryUris = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_URIS).orEmpty()
        val directoryNames = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_NAMES).orEmpty()
        val directoryCoverUris = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_URIS).orEmpty()
        val itemUris = if (uris.isNotEmpty()) uris else buildList {
            intent.data?.let { add(it) }
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add)
            }
        }.distinct()
        val items = itemUris.mapIndexed { index, uri ->
            val suppliedName = names.getOrNull(index)?.takeIf(String::isNotBlank)
            val suppliedMime = mimes.getOrNull(index)?.takeIf(String::isNotBlank)
            val hasSuppliedSize = sizes?.getOrNull(index) != null
            val suppliedSize = sizes?.getOrNull(index)?.takeIf { it >= 0L }
            val queried = if (suppliedName == null || suppliedMime == null || !hasSuppliedSize)
                queryItem(context, uri) else null to null
            SelectionItem(
                uri = uri,
                name = suppliedName ?: queried.first ?: uri.lastPathSegment.orEmpty(),
                mimeType = suppliedMime
                    ?: context.contentResolver.getType(uri) ?: "application/octet-stream",
                sizeBytes = suppliedSize ?: queried.second,
                directoryUri = directoryUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse),
                directoryName = directoryNames.getOrNull(index)?.takeIf(String::isNotBlank),
                directoryCoverUri = directoryCoverUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse),
                directoryArtworkMetadataResolved = directoryUris.getOrNull(index)?.isNotBlank() == true,
            )
        }
        val playlist = intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_PLAYLIST_URI)
            ?.let(Uri::parse)
            ?: intent.data?.takeIf { intent.type == JSPF_MIME }
        val metadata = intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_METADATA_URI)?.let(Uri::parse)
        return SelectionResult(items, playlist, metadata)
    }

    /** Create a readable JSPF document whose locations are the supplied content/file URIs. */
    fun createJspfPlaylist(context: Context, items: List<SelectionItem>, title: String = "Selection.jspf"): Uri {
        require(items.isNotEmpty()) { "A playlist needs at least one item" }
        val tracks = org.json.JSONArray()
        items.forEach { item ->
            val fields = org.json.JSONObject()
                .put("location", item.uri.toString())
                .put("title", item.name)
                .put("extension", org.json.JSONObject().put("io.github.gretchycat.infiniselect", org.json.JSONObject()
                    .put("mimeType", item.mimeType)
                    .put("sizeBytes", item.sizeBytes ?: org.json.JSONObject.NULL)
                    .put("directoryUri", item.directoryUri?.toString() ?: org.json.JSONObject.NULL)
                    .put("directoryName", item.directoryName ?: org.json.JSONObject.NULL)
                    .put("directoryCoverUri", item.directoryCoverUri?.toString() ?: org.json.JSONObject.NULL)
                    .put("directoryArtworkMetadataResolved", item.directoryArtworkMetadataResolved)))
            tracks.put(fields)
        }
        val json = org.json.JSONObject().put("playlist", org.json.JSONObject()
            .put("title", title.substringBeforeLast('.', title))
            .put("track", tracks))
        return SelectionDocumentProvider.create(context, title.ensureExtension("jspf"), JSPF_MIME,
            json.toString(2).toByteArray(Charsets.UTF_8))
    }

    /** Create an XML metadata document for the same URI list, without reading file contents. */
    fun createMetadataXml(context: Context, items: List<SelectionItem>, title: String = "Selection.xml"): Uri {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<selection xmlns=\"urn:github:gretchycat:infiniselect:selection:1\" title=\"")
            append(title.xmlEscape()).append("\">\n")
            items.forEach { item ->
                append("  <item uri=\"").append(item.uri.toString().xmlEscape())
                    .append("\" name=\"").append(item.name.xmlEscape())
                    .append("\" mimeType=\"").append(item.mimeType.xmlEscape()).append("\"")
                item.sizeBytes?.takeIf { it >= 0L }?.let { append(" sizeBytes=\"").append(it).append("\"") }
                append("/>\n")
            }
            append("</selection>\n")
        }
        return SelectionDocumentProvider.create(context, title.ensureExtension("xml"), "application/xml",
            xml.toByteArray(Charsets.UTF_8))
    }

    private fun queryItem(context: Context, uri: Uri): Pair<String?, Long?> = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) null to null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    (nameIndex.takeIf { it >= 0 }?.let(cursor::getString)) to
                        (sizeIndex.takeIf { it >= 0 }?.let(cursor::getLong)?.takeIf { it >= 0L })
                }
            } ?: (null to null)
    }.getOrDefault(null to null)

    private fun String.ensureExtension(extension: String) = if (substringAfterLast('.', "").equals(extension, true)) this else "$this.$extension"
    private fun String.xmlEscape() = replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    internal const val JSPF_MIME = "application/jspf+json"
}

/** Optional backend hook. Implementations are discovered through Java ServiceLoader. */
interface FileSourceBackend {
    val id: String
    val label: String
    val priority: Int get() = 100
    fun isAvailable(context: Context): Boolean
    fun createIntent(context: Context, request: OpenRequest): Intent
}

/** Runtime registration hook for apps that assemble source plugins dynamically. */
object FileSourceRegistry {
    private val registered = java.util.concurrent.CopyOnWriteArrayList<FileSourceBackend>()

    fun register(backend: FileSourceBackend) {
        registered.removeAll { it.id == backend.id }
        registered += backend
    }

    fun unregister(id: String) { registered.removeAll { it.id == id } }

    internal fun available(context: Context): List<FileSourceBackend> {
        val discovered = runCatching {
            java.util.ServiceLoader.load(FileSourceBackend::class.java, context.classLoader).toList()
        }.getOrDefault(emptyList())
        return (registered + discovered).distinctBy { it.id }
            .filter { runCatching { it.isAvailable(context) }.getOrDefault(false) }
            .sortedWith(compareBy<FileSourceBackend> { it.priority }.thenBy { it.label.lowercase() })
    }
}
