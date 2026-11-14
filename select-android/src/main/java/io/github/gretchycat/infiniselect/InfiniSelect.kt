package io.github.gretchycat.infiniselect

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.os.ResultReceiver
import android.database.Cursor
import android.provider.OpenableColumns
import java.io.File

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
    /** Package a multi-file selection as JSPF, retaining each item's authoritative URI. */
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
    /** Stable location identity paired with [uri], which may be a short-lived read relay. */
    val sourceReference: String? = null,
    /** Stable location identity for [directoryUri]. */
    val directoryReference: String? = null,
    /** Stable location identity for [directoryCoverUri]. */
    val directoryCoverReference: String? = null,
) {
    /** Short lived or persisted Android access URI. */
    val contentUri: Uri get() = uri
    /** Stable storage identity; never derive this from [contentUri] when a source reference exists. */
    val authoritativeUri: Uri get() = sourceReference?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?.takeUnless(Uri::isTemporarySelectionUri)
        ?: uri.takeUnless(Uri::isTemporarySelectionUri)
        ?: throw IllegalStateException("Selection relay has no authoritative URI")
}

/** A fresh Android-readable link paired with the durable backend location. */
data class ResolvedSource(
    val contentUri: Uri,
    val authoritativeUri: Uri,
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

data class SelectionResult(
    val items: List<SelectionItem>,
    val playlistUri: Uri? = null,
    val metadataUri: Uri? = null,
    val directoryReference: String? = null,
    /** Current Android access URI for the selected starting directory, if supplied. */
    val directoryContentUri: Uri? = null,
) {
    val primaryUri: Uri? get() = playlistUri ?: items.firstOrNull()?.uri
    /** Stable location identity paired with [directoryContentUri]. */
    val directoryAuthoritativeUri: Uri? get() = directoryReference
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
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

    /** True for an expiring selector relay, which is an access handle rather than a saved identity. */
    fun isTemporaryAccessUri(uri: Uri?): Boolean = uri?.isTemporarySelectionUri() == true

    /** Choose a stable identity candidate without interpreting the backend URI format. */
    fun authoritativeCandidate(reference: String?, fallback: Uri? = null): Uri? =
        sequenceOf(reference?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }, fallback)
            .filterNotNull()
            .firstOrNull { !it.isTemporarySelectionUri() }

    /** Select a durable URI string without interpreting its backend-specific syntax. */
    fun authoritativeLocationCandidate(reference: String?, fallback: Uri? = null): String? =
        sequenceOf(reference?.takeIf(String::isNotBlank), fallback?.toString())
            .filterNotNull()
            .firstOrNull { !isTemporarySelectionReference(it) }

    fun defaultDirectory(context: Context, clientId: String): Uri? {
        val uri = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(DIRECTORY_PREFIX + clientId, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return null
        if (uri.authority == INFINIFOLDER_DOCUMENTS_AUTHORITY &&
            context.packageManager.resolveContentProvider(INFINIFOLDER_DOCUMENTS_AUTHORITY, 0) == null) return null
        return uri
    }

    fun setDefaultDirectory(context: Context, clientId: String, uri: Uri?) {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "clientId must be a short stable identifier" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().apply {
            if (uri == null) remove(DIRECTORY_PREFIX + clientId)
            else putString(DIRECTORY_PREFIX + clientId, uri.toString())
        }.apply()
    }

    /**
     * Resolve a durable storage address to a fresh Android content link.
     * Remote, archive, filesystem-image, local-file and legacy InfiniFolder
     * references are delegated to InfiniFolder when it is installed. Ordinary
     * Android content URIs remain direct SAF/provider links when it is absent.
     */
    fun resolveSource(
        context: Context,
        reference: String?,
        displayName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
    ): ResolvedSource? = resolveSourceInternal(context, reference, displayName, mimeType, sizeBytes, false)

    /** Resolve for an actual read, retaining the provider's failure instead of silently returning a backend URI. */
    fun requireSource(
        context: Context,
        reference: String,
        displayName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
    ): ResolvedSource = resolveSourceInternal(context, reference, displayName, mimeType, sizeBytes, true)
        ?: throw SourceResolutionException("The resource has no resolvable authoritative location")

    private fun resolveSourceInternal(
        context: Context, reference: String?, displayName: String?, mimeType: String?, sizeBytes: Long?,
        reportFailure: Boolean,
    ): ResolvedSource? {
        if (reference.isNullOrBlank()) return null
        val requestedUri = runCatching { Uri.parse(reference) }.getOrNull() ?: return null
        val uri = authoritativeUri(context, requestedUri.toString()) ?: when {
            requestedUri.authority == INFINIFOLDER_DOCUMENTS_AUTHORITY ||
                requestedUri.isTemporarySelectionUri() -> return null
            else -> requestedUri
        }
        val legacyFolderReference = uri.scheme.equals("content", true) &&
            uri.authority == INFINIFOLDER_DOCUMENTS_AUTHORITY
        val directAndroidContent = uri.scheme.equals("content", true) &&
            !legacyFolderReference && !uri.authority.orEmpty().endsWith(SELECTION_AUTHORITY_SUFFIX, true)
        if (directAndroidContent && hasReadableAccessUri(context, uri)) {
            // A granted SAF URI is usable even when package visibility prevents
            // PackageManager from reporting its provider package.
            return ResolvedSource(uri, uri, displayName, mimeType, sizeBytes)
        }

        val extras = Bundle().apply {
            putString(EXTRA_SOURCE_URI, uri.toString())
            displayName?.let { putString(EXTRA_DISPLAY_NAME, it) }
            mimeType?.let { putString(EXTRA_MIME_TYPE, it) }
            sizeBytes?.let { putLong(EXTRA_SIZE_BYTES, it) }
        }
        val responseAttempt = runCatching {
            context.contentResolver.call(
                Uri.Builder().scheme("content").authority(INFINIFOLDER_SELECTION_AUTHORITY).build(),
                METHOD_RESOLVE_SOURCE,
                null,
                extras,
            )
        }
        val response = responseAttempt.getOrNull()
        val contentUri = response?.getString(EXTRA_CONTENT_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val authoritativeUri = response?.getString(EXTRA_AUTHORITATIVE_URI)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (contentUri?.scheme.equals("content", true) && authoritativeUri != null &&
            !isTemporaryAccessUri(authoritativeUri)) {
            return ResolvedSource(
                contentUri = requireNotNull(contentUri),
                authoritativeUri = authoritativeUri,
                displayName = response.getString(EXTRA_DISPLAY_NAME) ?: displayName,
                mimeType = response.getString(EXTRA_MIME_TYPE) ?: mimeType,
                sizeBytes = response.getLong(EXTRA_SIZE_BYTES, -1L).takeIf { it >= 0L } ?: sizeBytes,
            )
        }

        // Locally readable files and SAF documents remain usable without Folder.
        // A temporary relay can never stand in for the authoritative identity.
        val fallback = when {
            uri.scheme.equals("file", true) -> runCatching {
                val file = uri.path?.let(::File) ?: return null
                val name = displayName?.takeIf(String::isNotBlank) ?: file.name
                val mime = mimeType?.takeIf(String::isNotBlank)
                    ?: android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"
                val content = SelectionDocumentProvider.createFileLink(context, file, name, mime)
                ResolvedSource(content, uri, name, mime, sizeBytes ?: file.length())
            }.getOrNull()
            directAndroidContent && hasReadableAccessUri(context, uri) ->
                ResolvedSource(uri, uri, displayName, mimeType, sizeBytes)
            else -> null
        }
        if (fallback == null && reportFailure) {
            val cause = responseAttempt.exceptionOrNull()
            val message = when {
                cause != null -> "InfiniFolder could not resolve this resource: ${cause.message ?: cause.javaClass.simpleName}"
                response == null -> "The installed InfiniFolder cannot resolve saved storage locations. Install the current InfiniFolder build."
                else -> "InfiniFolder returned an incomplete storage access response"
            }
            throw SourceResolutionException(message, cause)
        }
        return fallback
    }

    /**
     * Resolve a selected resource while retaining its current Android access link
     * whenever the caller still has permission. Only request a fresh link from
     * InfiniFolder after that access link expires or is unavailable.
     */
    fun resolveSelectedSource(
        context: Context,
        contentUri: Uri?,
        authoritativeReference: String?,
        displayName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
    ): ResolvedSource? = resolveSelectedSourceInternal(
        context, contentUri, authoritativeReference, displayName, mimeType, sizeBytes, false)

    /** Strict lazy resolution for readers and players; failure never returns an inaccessible backend address. */
    fun requireSelectedSource(
        context: Context,
        contentUri: Uri?,
        authoritativeReference: String?,
        displayName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
    ): ResolvedSource = resolveSelectedSourceInternal(
        context, contentUri, authoritativeReference, displayName, mimeType, sizeBytes, true)
        ?: throw SourceResolutionException("The resource has no resolvable authoritative location")

    private fun resolveSelectedSourceInternal(
        context: Context, contentUri: Uri?, authoritativeReference: String?, displayName: String?,
        mimeType: String?, sizeBytes: Long?, reportFailure: Boolean,
    ): ResolvedSource? {
        val authoritative = authoritativeUri(context, authoritativeReference)
            ?: authoritativeCandidate(authoritativeReference, contentUri)
        val current = contentUri?.toString()
        val preferred = selectCurrentAccessLocation(
            contentUri = current,
            authoritativeUri = authoritative?.toString(),
            hasReadAccess = contentUri?.let { hasReadableAccessUri(context, it) } == true,
        )
        if (preferred != null) {
            return ResolvedSource(
                contentUri = Uri.parse(preferred.contentUri),
                authoritativeUri = Uri.parse(preferred.authoritativeUri),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            )
        }
        return resolveSourceInternal(context, authoritative?.toString() ?: current,
            displayName, mimeType, sizeBytes, reportFailure)
    }

    private fun hasReadableAccessUri(context: Context, uri: Uri): Boolean {
        if (uri.scheme.equals("file", true)) return uri.path?.let(::File)?.canRead() == true
        if (!uri.scheme.equals("content", true)) return false
        if (context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true
        // Public metadata is not proof of permission to read a document's bytes.
        // Providers without an explicit grant must demonstrate an openable handle;
        // close it immediately without reading, copying or materializing the body.
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
    }

    /** Compatibility convenience for callers that only need the current content URI. */
    fun resolveSourceReference(context: Context, reference: String?): Uri? =
        resolveSource(context, reference)?.contentUri

    /** Canonicalize a legacy or provider-owned reference without creating an access link. */
    fun authoritativeUri(context: Context?, reference: String?): Uri? {
        if (reference.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return null
        val temporaryRelay = uri.isTemporarySelectionUri()
        val legacyFolderReference = uri.scheme.equals("content", true) &&
            uri.authority == INFINIFOLDER_DOCUMENTS_AUTHORITY
        if (!temporaryRelay && !legacyFolderReference) return uri
        if (context == null) return if (temporaryRelay) null else uri
        val result = context?.let { appContext -> runCatching {
            appContext.contentResolver.call(
                Uri.Builder().scheme("content").authority(INFINIFOLDER_SELECTION_AUTHORITY).build(),
                METHOD_CANONICALIZE_SOURCE,
                null,
                Bundle().apply { putString(EXTRA_SOURCE_URI, uri.toString()) },
            )?.getString(EXTRA_AUTHORITATIVE_URI)
        }.getOrNull() }
        // A legacy InfiniFolder DocumentsProvider URI remains the durable
        // identity if the optional app is absent. Canonicalize it when the
        // provider can answer, but never replace it with a relay URI.
        return result?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeUnless { it.isTemporarySelectionUri() }
            ?: if (temporaryRelay) null else uri
    }

    /**
     * Resolve a playlist or directory-relative location without exposing backend
     * URI syntax to clients. Archive traversal is delegated to InfiniFolder;
     * ordinary URI and Android DocumentsProvider resolution stays in this library.
     */
    fun resolveRelativeLocation(context: Context?, authoritativeBase: Uri, relativePath: String): Uri? {
        val result = resolveRelativeLocationText(context, authoritativeBase.toString(), relativePath)
            ?: return null
        return runCatching { Uri.parse(result) }.getOrNull()
    }

    /** String form for clients that preserve their own URI wrapper while the library resolves paths. */
    fun resolveRelativeLocationText(context: Context?, authoritativeBase: String, relativePath: String): String? {
        val reference = relativePath.trim()
        if (reference.isEmpty()) return authoritativeBase
        val parsedReference = runCatching {
            java.net.URI(reference.replace(" ", "%20"))
        }.getOrNull()
        if (!reference.startsWith("//") && parsedReference?.isAbsolute == true) {
            return authoritativeUri(context, reference)?.toString() ?: androidUriString(parsedReference)
        }

        val parsedBase = runCatching {
            java.net.URI(authoritativeBase.replace(" ", "%20"))
        }.getOrNull() ?: return null
        val baseScheme = parsedBase.scheme.orEmpty()
        val accessUri = if (baseScheme.equals("content", true) || baseScheme.equals("archive", true))
            runCatching { Uri.parse(authoritativeBase) }.getOrNull() else null
        val canonicalBase = if (baseScheme.equals("content", true) &&
            (parsedBase.rawAuthority == INFINIFOLDER_DOCUMENTS_AUTHORITY ||
                parsedBase.rawAuthority?.endsWith(SELECTION_AUTHORITY_SUFFIX, true) == true)) {
            authoritativeUri(context, authoritativeBase)?.toString()
        } else null
        if (canonicalBase != null && canonicalBase != authoritativeBase) {
            return resolveRelativeLocationText(context, canonicalBase, reference)
        }
        // An expired access handle cannot serve as a playlist's containing
        // location. Do not resolve a relative path against its opaque token.
        if (isTemporarySelectionReference(authoritativeBase)) return null

        if (baseScheme.equals("archive", true)) {
            if (accessUri == null) return null
            val extras = Bundle().apply {
                putString(EXTRA_BASE_URI, authoritativeBase)
                putString(EXTRA_RELATIVE_PATH, reference)
            }
            val resolved = context?.let { appContext -> runCatching {
                appContext.contentResolver.call(
                    Uri.Builder().scheme("content").authority(INFINIFOLDER_SELECTION_AUTHORITY).build(),
                    METHOD_RESOLVE_RELATIVE_ARCHIVE,
                    null,
                    extras,
                )?.getString(EXTRA_RESOLVED_URI)
            }.getOrNull() }
            return resolved
        }

        if (baseScheme.equals("content", true) && accessUri != null) {
            val isTree = runCatching { android.provider.DocumentsContract.isTreeUri(accessUri) }
                .getOrDefault(false)
            val isDocument = context != null && runCatching {
                android.provider.DocumentsContract.isDocumentUri(context, accessUri)
            }.getOrDefault(false)
            if (isDocument) {
                val authority = accessUri.authority ?: return null
                val documentId = runCatching {
                    android.provider.DocumentsContract.getDocumentId(accessUri)
                }.getOrNull() ?: return null
                val colon = documentId.indexOf(':')
                val prefix = if (colon >= 0) documentId.substring(0, colon + 1) else ""
                val path = if (colon >= 0) documentId.substring(colon + 1) else documentId
                val baseDirectory = path.substringBeforeLast('/', "")
                val decodedReference = Uri.decode(reference.replace("+", "%2B"))
                val target = if (decodedReference.startsWith('/')) normalizePath("", decodedReference)
                    else normalizePath(baseDirectory, decodedReference)
                val targetId = prefix + target
                return runCatching {
                    if (isTree) android.provider.DocumentsContract.buildDocumentUriUsingTree(accessUri, targetId)
                    else android.provider.DocumentsContract.buildDocumentUri(authority, targetId)
                }.getOrNull()?.toString()
            }
            if (isTree) {
                val documentId = runCatching {
                    android.provider.DocumentsContract.getTreeDocumentId(accessUri)
                }.getOrNull() ?: return null
                val colon = documentId.indexOf(':')
                val prefix = if (colon >= 0) documentId.substring(0, colon + 1) else ""
                val path = if (colon >= 0) documentId.substring(colon + 1) else documentId
                val decodedReference = Uri.decode(reference.replace("+", "%2B"))
                val target = if (decodedReference.startsWith('/')) normalizePath("", decodedReference)
                    else normalizePath(path, decodedReference)
                return runCatching {
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        accessUri, prefix + target)
                }.getOrNull()?.toString()
            }
        }

        val encodedReference = reference.replace(" ", "%20")
        return runCatching { androidUriString(parsedBase.resolve(encodedReference).normalize()) }.getOrNull()
    }

    private fun androidUriString(uri: java.net.URI): String {
        if (uri.scheme.equals("file", true) && uri.rawAuthority == null && uri.rawPath?.startsWith('/') == true) {
            return "file://${uri.rawPath}" + (uri.rawQuery?.let { "?$it" } ?: "") +
                (uri.rawFragment?.let { "#$it" } ?: "")
        }
        return uri.toASCIIString()
    }

    /** Return the best display name without exposing archive or backend syntax to callers. */
    fun displayName(context: Context?, authoritativeUri: Uri, fallback: String? = null): String? {
        if (authoritativeUri.scheme.equals("archive", true)) {
            val result = context?.let { appContext -> runCatching {
                appContext.contentResolver.call(
                    Uri.Builder().scheme("content").authority(INFINIFOLDER_SELECTION_AUTHORITY).build(),
                    METHOD_DESCRIBE_ARCHIVE,
                    null,
                    Bundle().apply { putString(EXTRA_SOURCE_URI, authoritativeUri.toString()) },
                )?.getString(EXTRA_DISPLAY_NAME)
            }.getOrNull() }
            if (!result.isNullOrBlank()) return result
        }
        val pathName = authoritativeUri.lastPathSegment?.let(Uri::decode)?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
        return pathName ?: fallback?.takeIf(String::isNotBlank)
    }

    private fun normalizePath(baseDirectory: String, relative: String): String {
        val segments = baseDirectory.replace('\\', '/').split('/').filter(String::isNotEmpty).toMutableList()
        for (part in relative.replace('\\', '/').split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
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
        val sourceReferences = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_AUTHORITATIVE_URIS)
            ?: intent.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_SOURCE_REFERENCES).orEmpty()
        val directoryReferences = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_AUTHORITATIVE_URIS)
            ?: intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_REFERENCES).orEmpty()
        val directoryCoverReferences = intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_AUTHORITATIVE_URIS)
            ?: intent.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_REFERENCES).orEmpty()
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
            val suppliedSource = sourceReferences.getOrNull(index)?.takeIf(String::isNotBlank)
                ?: uri.toString()
            val authoritativeSource = authoritativeUri(context, suppliedSource)
                ?: throw IllegalArgumentException("Selection relay omitted its authoritative URI")
            val directoryAccessUri = directoryUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse)
            val coverAccessUri = directoryCoverUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse)
            SelectionItem(
                uri = uri,
                name = suppliedName ?: queried.first ?: uri.lastPathSegment.orEmpty(),
                mimeType = suppliedMime
                    ?: context.contentResolver.getType(uri) ?: "application/octet-stream",
                sizeBytes = suppliedSize ?: queried.second,
                sourceReference = authoritativeSource.toString(),
                directoryUri = directoryAccessUri,
                directoryName = directoryNames.getOrNull(index)?.takeIf(String::isNotBlank),
                directoryReference = stableLocation(context,
                    directoryReferences.getOrNull(index), directoryAccessUri),
                directoryCoverUri = coverAccessUri,
                directoryCoverReference = stableLocation(context,
                    directoryCoverReferences.getOrNull(index), coverAccessUri),
                directoryArtworkMetadataResolved = directoryUris.getOrNull(index)?.isNotBlank() == true,
            )
        }
        val playlist = intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_PLAYLIST_URI)
            ?.let(Uri::parse)
            ?: intent.data?.takeIf { intent.type == JSPF_MIME }
        val metadata = intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_METADATA_URI)?.let(Uri::parse)
        val directoryAccessUri = intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY)
            ?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val directoryReference = stableLocation(context,
            intent.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY_REFERENCE), directoryAccessUri)
        return SelectionResult(items, playlist, metadata, directoryReference, directoryAccessUri)
    }

    /** Create a readable JSPF document with durable backend locations where available. */
    fun createJspfPlaylist(context: Context, items: List<SelectionItem>, title: String = "Selection.jspf"): Uri {
        require(items.isNotEmpty()) { "A playlist needs at least one item" }
        val tracks = org.json.JSONArray()
        items.forEach { item ->
            val sourceLocation = stableLocation(context, item.sourceReference, item.contentUri)
                ?: throw IllegalArgumentException("Cannot serialize an item without an authoritative URI")
            val directoryLocation = stableLocation(context, item.directoryReference, item.directoryUri)
            val coverLocation = stableLocation(context, item.directoryCoverReference, item.directoryCoverUri)
            val fields = org.json.JSONObject()
                .put("location", sourceLocation)
                .put("title", item.name)
                .put("extension", org.json.JSONObject().put("io.github.gretchycat.infiniselect", org.json.JSONObject()
                    .put("mimeType", item.mimeType)
                    .put("sizeBytes", item.sizeBytes ?: org.json.JSONObject.NULL)
                    .put("authoritativeUri", sourceLocation)
                    .put("sourceReference", sourceLocation)
                    .put("directoryUri", directoryLocation ?: org.json.JSONObject.NULL)
                    .put("directoryName", item.directoryName ?: org.json.JSONObject.NULL)
                    .put("directoryReference", directoryLocation ?: org.json.JSONObject.NULL)
                    .put("directoryCoverUri", coverLocation ?: org.json.JSONObject.NULL)
                    .put("directoryCoverReference", coverLocation ?: org.json.JSONObject.NULL)
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
                val sourceLocation = stableLocation(context, item.sourceReference, item.contentUri)
                    ?: throw IllegalArgumentException("Cannot serialize an item without an authoritative URI")
                val directoryLocation = stableLocation(context, item.directoryReference, item.directoryUri)
                val coverLocation = stableLocation(context, item.directoryCoverReference, item.directoryCoverUri)
                append("  <item uri=\"").append(item.contentUri.toString().xmlEscape())
                    .append("\" contentUri=\"").append(item.contentUri.toString().xmlEscape())
                    .append("\" authoritativeUri=\"").append(sourceLocation.xmlEscape())
                    .append("\" sourceReference=\"").append(sourceLocation.xmlEscape())
                    .append("\" name=\"").append(item.name.xmlEscape())
                    .append("\" mimeType=\"").append(item.mimeType.xmlEscape()).append("\"")
                item.directoryUri?.let { accessUri ->
                    append(" directoryContentUri=\"").append(accessUri.toString().xmlEscape()).append("\"")
                }
                directoryLocation?.let { append(" directoryReference=\"").append(it.xmlEscape()).append("\"") }
                item.directoryCoverUri?.takeUnless(Uri::isTemporarySelectionUri)?.let {
                    append(" directoryCoverContentUri=\"").append(it.toString().xmlEscape()).append("\"")
                }
                coverLocation?.let { append(" directoryCoverReference=\"").append(it.xmlEscape()).append("\"") }
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

    private fun stableLocation(context: Context, reference: String?, accessUri: Uri?): String? {
        return sequenceOf(reference?.takeIf(String::isNotBlank), accessUri?.toString())
            .filterNotNull()
            .mapNotNull { authoritativeUri(context, it)?.toString() }
            .firstOrNull()
    }

    private fun String.ensureExtension(extension: String) = if (substringAfterLast('.', "").equals(extension, true)) this else "$this.$extension"
    private fun String.xmlEscape() = replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    const val JSPF_MIME = "application/jspf+json"
    private const val INFINIFOLDER_DOCUMENTS_AUTHORITY = FileSelectionContract.INFINIFOLDER_DOCUMENTS_AUTHORITY
    private const val INFINIFOLDER_SELECTION_AUTHORITY = FileSelectionContract.INFINIFOLDER_SELECTION_AUTHORITY
    private const val SELECTION_AUTHORITY_SUFFIX = ".selection"
    private const val METHOD_RESOLVE_SOURCE = FileSelectionContract.STORAGE_METHOD_RESOLVE_SOURCE
    private const val METHOD_RESOLVE_RELATIVE_ARCHIVE = FileSelectionContract.STORAGE_METHOD_RESOLVE_RELATIVE_ARCHIVE
    private const val METHOD_DESCRIBE_ARCHIVE = FileSelectionContract.STORAGE_METHOD_DESCRIBE_ARCHIVE
    private const val METHOD_CANONICALIZE_SOURCE = FileSelectionContract.STORAGE_METHOD_CANONICALIZE_SOURCE
    private const val EXTRA_SOURCE_URI = FileSelectionContract.STORAGE_EXTRA_SOURCE_URI
    private const val EXTRA_CONTENT_URI = FileSelectionContract.STORAGE_EXTRA_CONTENT_URI
    private const val EXTRA_AUTHORITATIVE_URI = FileSelectionContract.STORAGE_EXTRA_AUTHORITATIVE_URI
    private const val EXTRA_DISPLAY_NAME = FileSelectionContract.STORAGE_EXTRA_DISPLAY_NAME
    private const val EXTRA_MIME_TYPE = FileSelectionContract.STORAGE_EXTRA_MIME_TYPE
    private const val EXTRA_SIZE_BYTES = FileSelectionContract.STORAGE_EXTRA_SIZE_BYTES
    private const val EXTRA_BASE_URI = FileSelectionContract.STORAGE_EXTRA_BASE_URI
    private const val EXTRA_RELATIVE_PATH = FileSelectionContract.STORAGE_EXTRA_RELATIVE_PATH
    private const val EXTRA_RESOLVED_URI = FileSelectionContract.STORAGE_EXTRA_RESOLVED_URI
}

internal data class SelectedSourceLocations(val contentUri: String, val authoritativeUri: String)

/** Pure selection rule shared with JVM contract tests. */
internal fun selectCurrentAccessLocation(
    contentUri: String?,
    authoritativeUri: String?,
    hasReadAccess: Boolean,
): SelectedSourceLocations? {
    if (!hasReadAccess || contentUri.isNullOrBlank()) return null
    val identity = authoritativeUri?.takeIf(String::isNotBlank)
        ?: contentUri.takeUnless(::isTemporarySelectionReference)
        ?: return null
    return SelectedSourceLocations(contentUri, identity)
}

private fun Uri.isTemporarySelectionUri(): Boolean =
    isTemporarySelectionReference(toString())

private fun isTemporarySelectionReference(reference: String): Boolean {
    if (!reference.startsWith("content://", true)) return false
    val authority = reference.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.endsWith(".selection", true)
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
