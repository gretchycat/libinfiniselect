package io.github.gretchycat.infiniselect

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Chooses InfiniFolder when installed, otherwise presents SAF and installed source hooks. */
class FileSelectActivity : Activity() {
    private lateinit var request: OpenRequest
    private var saveRequest: SaveRequest? = null
    private var waitingForInfiniFolder = false
    private var inSourceChooser = false
    private var sourceMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The host may show InfiniFolder's floating selector over another app. This
        // activity exists only to relay its result, so keep the handoff invisible and
        // non-interactive while the overlay is loading.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        if (intent.action == ACTION_SAVE) {
            saveRequest = intent.toSaveRequest() ?: run { finish(); return }
            startPreferredSaveSource()
        } else {
            request = intent.toRequest() ?: run { finish(); return }
            startPreferredSource()
        }
    }

    private fun startPreferredSaveSource() {
        val request = saveRequest ?: run { finish(); return }
        if (isInfiniFolderInstalled()) {
            val source = Intent(FileSelectionContract.ACTION_SAVE)
                .setPackage(INFINIFOLDER_PACKAGE)
                .putExtra(FileSelectionContract.EXTRA_CLIENT_ID, request.clientId)
                .putExtra(FileSelectionContract.EXTRA_DISPLAY_NAME, request.displayName)
                .putExtra(FileSelectionContract.EXTRA_SAVE_MODE, true)
                .putExtra(FileSelectionContract.EXTRA_SAVE_FILE_NAME, request.fileName)
                .putExtra(FileSelectionContract.EXTRA_SAVE_MIME_TYPE, request.mimeType)
                .putExtra(FileSelectionContract.EXTRA_SAVE_SOURCE_URI, request.sourceUri)
                .putExtra(FileSelectionContract.EXTRA_SAVE_SOURCE_SIZE, request.sourceSizeBytes ?: -1L)
                .apply {
                    request.initialDirectory?.let { putExtra(FileSelectionContract.EXTRA_INITIAL_DIRECTORY, it) }
                    clipData = android.content.ClipData.newRawUri(request.fileName, request.sourceUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            try {
                startActivityForResult(source, REQUEST_INFINIFOLDER_SAVE)
                return
            } catch (_: Exception) { }
        }
        launchSafSavePicker()
    }

    private fun launchSafSavePicker() {
        val request = saveRequest ?: run { finish(); return }
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = request.mimeType
            putExtra(Intent.EXTRA_TITLE, request.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            safInitialDirectory(request.initialDirectory)?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { startActivityForResult(picker, REQUEST_SAF_SAVE) }
            .onFailure { finishSaveFailure("No file picker is available: ${it.message ?: "unavailable"}") }
    }

    private fun startPreferredSource() {
        if (intent.getBooleanExtra("io.github.gretchycat.infiniselect.WRITABLE", false)) {
            launchSafPicker()
            return
        }
        if (isInfiniFolderInstalled()) {
            val selection = Intent(FileSelectionContract.ACTION_SELECT)
                .setPackage(INFINIFOLDER_PACKAGE)
                .putExtras(request.toBundle())
                // InfiniFolder completes selection through a ResultReceiver, so
                // Activity.getCallingPackage() is not reliable in its adapter.
                .putExtra(FileSelectionContract.EXTRA_REQUESTING_PACKAGE, packageName)
            waitingForInfiniFolder = true
            try {
                startActivityForResult(selection, REQUEST_INFINIFOLDER)
                return
            } catch (_: Exception) {
                waitingForInfiniFolder = false
            }
        }
        showSourceChooser()
    }

    private fun isInfiniFolderInstalled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) packageManager.getPackageInfo(
            INFINIFOLDER_PACKAGE, PackageManager.PackageInfoFlags.of(0)
        ) else @Suppress("DEPRECATION") packageManager.getPackageInfo(INFINIFOLDER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun safInitialDirectory(uri: Uri?): Uri? = uri?.takeUnless {
        it.authority == "com.infinifolder.documents" && !isInfiniFolderInstalled()
    }

    private fun showSourceChooser() {
        inSourceChooser = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = themeColor(android.R.attr.colorBackground, if (night) 0xFF202124.toInt() else 0xFFFAFAFA.toInt())
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.TOP
            setBackgroundColor(backgroundColor)
        }
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appIcon = ImageView(this).apply {
            val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            if (icon != null) setImageDrawable(icon)
            contentDescription = request.displayName
            layoutParams = LinearLayout.LayoutParams((56 * resources.displayMetrics.density).toInt(), (56 * resources.displayMetrics.density).toInt())
        }
        identity.addView(appIcon)
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, 0, 0, 0) }
        labels.addView(TextView(this).apply {
            text = request.displayName
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply { text = "Choose a source to open a file"; textSize = 14f })
        identity.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(identity)
        sourceMessage?.let { message ->
            page.addView(TextView(this).apply {
                text = message
                textSize = 13f
                setPadding(0, pad / 2, 0, 0)
            })
        }

        val sourceScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad }
        }
        val sourceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val backends = FileSourceRegistry.available(this)
        backends.forEach { backend ->
            sourceRow.addView(Button(this).apply {
                text = backend.label
                setOnClickListener {
                    runCatching { startActivityForResult(backend.createIntent(this@FileSelectActivity, request), REQUEST_BACKEND) }
                        .onFailure { showMessage("${backend.label} could not be opened: ${it.message ?: "unavailable"}") }
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = pad / 3 })
        }
        sourceRow.addView(Button(this).apply {
            text = "Android files"
            setOnClickListener { launchSafPicker() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = pad / 3 })
        sourceScroll.addView(sourceRow)
        page.addView(sourceScroll)

        page.addView(Button(this).apply {
            text = "Set starting folder"
            setOnClickListener { launchDefaultFolderPicker() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad / 2 })
        page.addView(TextView(this).apply {
            text = "Starting folder: ${request.initialDirectory?.lastPathSegment ?: "not set"}"
            textSize = 12f
            alpha = .75f
            setPadding(0, pad / 3, 0, pad / 2)
        })

        page.addView(TextView(this).apply {
            text = "Choose a source above. Your starting folder is saved separately for this app."
            textSize = 13f
            alpha = .75f
            setPadding(0, pad, 0, 0)
        })
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
            addView(page, ViewGroup.LayoutParams(-1, -2))
        })
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(attribute, value, true) && value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT)
            value.data else fallback
    }

    private fun launchSafPicker() {
        val types = request.mimeTypes.toTypedArray()
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (types.size == 1) types[0] else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.allowMultiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (intent.getBooleanExtra("io.github.gretchycat.infiniselect.WRITABLE", false)) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val initial = safInitialDirectory(request.initialDirectory)
                ?: packageManager.resolveContentProvider("com.infinifolder.documents", 0)
                ?.let { DocumentsContract.buildDocumentUri(it.authority, "root") }
            initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { startActivityForResult(picker, REQUEST_SAF) }
            .onFailure { showMessage("Android's file picker is unavailable: ${it.message ?: "no picker app"}") }
    }

    private fun launchDefaultFolderPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            safInitialDirectory(request.initialDirectory)?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { startActivityForResult(picker, REQUEST_DEFAULT_DIRECTORY) }
            .onFailure { showMessage("Android's folder picker is unavailable: ${it.message ?: "no picker app"}") }
    }

    @Deprecated("Platform-minimum Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_INFINIFOLDER -> {
                waitingForInfiniFolder = false
                data?.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY)?.let { value ->
                    rememberDefaultDirectory(request.clientId, value)
                }
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getBooleanExtra(FileSelectionContract.EXTRA_FALLBACK_TO_SOURCES, false)) {
                        sourceMessage = data.getStringExtra(FileSelectionContract.EXTRA_FAILURE_MESSAGE)
                        showSourceChooser()
                    } else {
                        // InfiniFolder returns relay URIs in ClipData. Retain
                        // those grants before forwarding the normalized
                        // playlist result to the calling viewer.
                        retainGrant(data)
                        returnToCaller(resultCode, data)
                    }
                } else finishWithCancel()
            }
            REQUEST_SAF, REQUEST_BACKEND -> if (resultCode == RESULT_OK && data != null) {
                rememberDirectoryFromResult(request.clientId, data)
                retainGrant(data)
                returnToCaller(resultCode, data)
            } else if (inSourceChooser) {
                showSourceChooser()
            } else finishWithCancel()
            REQUEST_DEFAULT_DIRECTORY -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.let { uri ->
                        retainGrant(data)
                        InfiniSelect.setDefaultDirectory(this, request.clientId, uri)
                        request = request.copy(initialDirectory = uri)
                    }
                }
                showSourceChooser()
            }
            REQUEST_INFINIFOLDER_SAVE -> {
                data?.let { result ->
                    val directory = result.getStringExtra(FileSelectionContract.EXTRA_SAVE_DIRECTORY_REFERENCE)
                        ?: result.getStringExtra(FileSelectionContract.EXTRA_SAVE_DIRECTORY)
                        ?: result.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY)
                    directory?.let { rememberDefaultDirectory(saveRequest?.clientId, it) }
                }
                if (resultCode == RESULT_OK && data?.getBooleanExtra(FileSelectionContract.EXTRA_SAVE_SUCCESS, false) == true) {
                    setResult(RESULT_OK, data)
                    finish()
                } else if (data?.getBooleanExtra(FileSelectionContract.EXTRA_FALLBACK_TO_SOURCES, false) == true) {
                    launchSafSavePicker()
                } else if (resultCode == RESULT_OK) {
                    launchSafSavePicker()
                } else finishWithCancel()
            }
            REQUEST_SAF_SAVE -> {
                if (resultCode != RESULT_OK || data?.data == null) { finishWithCancel(); return }
                retainGrant(data)
                copySaveSourceTo(data.data!!, saveRequest?.clientId, data.flags)
            }
        }
    }

    private fun copySaveSourceTo(destination: Uri, clientId: String?, grantFlags: Int) {
        val request = saveRequest ?: run { finish(); return }
        Thread({
            var copied = false
            val result = runCatching {
                val input = contentResolver.openInputStream(request.sourceUri)
                    ?: error("The playlist data could not be opened")
                val output = contentResolver.openOutputStream(destination, "wt")
                    ?: error("The selected destination could not be opened")
                var written = 0L
                input.use { source -> output.use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        written += count
                    }
                } }
                if (request.sourceSizeBytes != null && written != request.sourceSizeBytes) {
                    error("Saved size did not match the playlist data")
                }
                copied = true
            }
            runOnUiThread {
                if (result.isSuccess) {
                    val directory = directoryForDocument(destination)
                    directory?.let { rememberDefaultDirectory(clientId, it.toString()) }
                    finishSaveSuccess(destination, request.fileName, directory, grantFlags)
                }
                else {
                    if (!copied) runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
                    finishSaveFailure("Could not save document: ${result.exceptionOrNull()?.message ?: "write failed"}")
                }
            }
        }, "InfiniSelectSafSave").start()
    }

    private fun finishSaveSuccess(uri: Uri, name: String, directory: Uri?, grantFlags: Int) {
        setResult(RESULT_OK, Intent().setDataAndType(uri, saveRequest?.mimeType).apply {
            addFlags(grantFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            clipData = android.content.ClipData.newRawUri(name, uri)
            putExtra(FileSelectionContract.EXTRA_SAVE_SUCCESS, true)
            putExtra(FileSelectionContract.EXTRA_SAVE_RESULT_NAME, name)
            directory?.let { putExtra(FileSelectionContract.EXTRA_SAVE_DIRECTORY, it.toString()) }
        })
        finish()
    }

    private fun rememberDirectoryFromResult(clientId: String, data: Intent) {
        val explicit = data.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY)?.let(Uri::parse)
        val directory = explicit ?: data.data?.let(::directoryForDocument)
        directory?.let { InfiniSelect.setDefaultDirectory(this, clientId, it) }
    }

    private fun rememberDefaultDirectory(clientId: String?, value: String) {
        val directory = runCatching { Uri.parse(value) }.getOrNull() ?: return
        clientId?.let { id -> runCatching { InfiniSelect.setDefaultDirectory(this, id, directory) } }
    }

    /** Return the containing document URI so SAF opens in the folder last used. */
    private fun directoryForDocument(uri: Uri): Uri? = runCatching {
        if (!DocumentsContract.isDocumentUri(this, uri)) return@runCatching null
        val documentId = DocumentsContract.getDocumentId(uri)
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentId.isBlank()) null else DocumentsContract.buildDocumentUri(uri.authority, parentId)
    }.getOrNull()

    private fun finishSaveFailure(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra(FileSelectionContract.EXTRA_SAVE_ERROR, message))
        finish()
    }

    private fun retainGrant(data: Intent) {
        // Only SAF results can grant persistable access. InfiniFolder relays are
        // temporary grants; trying one persistable-grant Binder call per item
        // needlessly stalls large playlist handoffs.
        if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags == 0) return
        val uris = linkedSetOf<Uri>()
        data.data?.let(uris::add)
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri
                ?.takeUnless { it.authority?.endsWith(".selection", ignoreCase = true) == true }
                ?.let(uris::add)
        }
        uris.filterNot { it.authority?.endsWith(".selection", ignoreCase = true) == true }
            .forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, flags) } }
    }

    private fun returnToCaller(result: Int, data: Intent) {
        if (result != RESULT_OK) { setResult(result, data); finish(); return }
        val items = selectedItems(data)
        if (items.isEmpty()) { finishWithCancel(); return }

        val playlistUri = if (request.allowMultiple && request.multipleSelectionDelivery == MultipleSelectionDelivery.JSPF && items.size > 1)
            InfiniSelect.createJspfPlaylist(this, items) else null
        val metadataUri = if (request.metadataFormat == SelectionMetadataFormat.XML)
            InfiniSelect.createMetadataXml(this, items) else null
        val first = items.first()
        val primary = playlistUri ?: first.uri
        val grantedPrimary = playlistUri ?: InfiniSelect.resolveSourceReference(this, first.sourceReference)
            ?: first.uri
        val primaryMime = if (playlistUri != null) InfiniSelect.JSPF_MIME else first.mimeType
        // The picker may return cross-app content URIs (for example, InfiniFolder
        // relay URIs). Re-grant every selected URI to our caller before forwarding
        // them in ClipData; otherwise Android can reject finish() while delivering
        // a multi-item result, crashing the picker and dropping the selection.
        val recipientPackage = callingPackage ?: callingActivity?.packageName
        if (recipientPackage != null && recipientPackage != packageName) {
            val resultDirectoryReference = data.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY_REFERENCE)
                ?: data.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY)
            val persistentReferences = buildSet {
                items.mapNotNullTo(this) { it.sourceReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryCoverReference?.let(Uri::parse) }
                resultDirectoryReference?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(::add)
            }
            val forwardedUris = buildList {
                add(primary)
                items.forEach { add(it.uri) }
                items.mapNotNullTo(this) { it.sourceReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryUri }
                items.mapNotNullTo(this) { it.directoryReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryCoverUri }
                items.mapNotNullTo(this) { it.directoryCoverReference?.let(Uri::parse) }
                resultDirectoryReference?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(::add)
                metadataUri?.let(::add)
            }.distinct()
            runCatching {
                forwardedUris.forEach { uri ->
                    val canPersist = data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
                        uri in persistentReferences
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (canPersist)
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION else 0
                    grantUriPermission(recipientPackage, uri, flags)
                }
            }.onFailure { error ->
                setResult(RESULT_CANCELED, Intent().putExtra(
                    FileSelectionContract.EXTRA_FAILURE_MESSAGE,
                    error.message ?: "Could not grant access to the selected files"
                ))
                finish()
                return
            }
        }
        data.setDataAndType(grantedPrimary, primaryMime)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_URI, first.uri.toString())
        data.putExtra(FileSelectionContract.EXTRA_RESULT_NAME, first.name)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_MIME, first.mimeType)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_SIZE, first.sizeBytes ?: -1L)
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_URIS, ArrayList(items.map { it.uri.toString() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_NAMES, ArrayList(items.map { it.name }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_MIMES, ArrayList(items.map { it.mimeType }))
        data.putExtra(FileSelectionContract.EXTRA_RESULT_SIZES, items.map { it.sizeBytes ?: -1L }.toLongArray())
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_SOURCE_REFERENCES,
            ArrayList(items.map { it.sourceReference ?: it.uri.toString() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_URIS,
            ArrayList(items.map { it.directoryUri?.toString().orEmpty() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_NAMES,
            ArrayList(items.map { it.directoryName.orEmpty() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_REFERENCES,
            ArrayList(items.map { it.directoryReference ?: it.directoryUri?.toString().orEmpty() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_URIS,
            ArrayList(items.map { it.directoryCoverUri?.toString().orEmpty() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_REFERENCES,
            ArrayList(items.map { it.directoryCoverReference ?: it.directoryCoverUri?.toString().orEmpty() }))
        playlistUri?.let { data.putExtra(FileSelectionContract.EXTRA_RESULT_PLAYLIST_URI, it.toString()) }
        metadataUri?.let { data.putExtra(FileSelectionContract.EXTRA_RESULT_METADATA_URI, it.toString()) }
        data.putExtra("displayName", if (playlistUri != null) "Selection.jspf" else first.name)
        data.putExtra("mimeType", primaryMime)
        // Consumers may keep a working queue beyond this Activity's lifetime.
        // Providers that support persisted URI grants can then let them retain
        // access; unsupported providers simply reject takePersistableUriPermission.
        val persistableGrant = data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        data.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or persistableGrant or
            (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val clipUris = if (playlistUri != null) {
            // Keep stable references in ClipData so Android can persist their
            // document-provider grants; the JSPF carries the matching strings.
            buildList {
                add(primary)
                items.mapNotNullTo(this) { it.sourceReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryReference?.let(Uri::parse) }
                items.mapNotNullTo(this) { it.directoryCoverReference?.let(Uri::parse) }
                data.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY_REFERENCE)
                    ?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(::add)
                metadataUri?.let(::add)
            }.distinct()
        } else buildList {
            items.mapNotNullTo(this) { it.sourceReference?.let(Uri::parse) }
            items.mapNotNullTo(this) { it.directoryUri }
            items.mapNotNullTo(this) { it.directoryReference?.let(Uri::parse) }
            items.mapNotNullTo(this) { it.directoryCoverReference?.let(Uri::parse) }
            data.getStringExtra(FileSelectionContract.EXTRA_RESULT_DIRECTORY_REFERENCE)
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(::add)
            metadataUri?.let(::add)
        }.distinct()
        data.clipData = android.content.ClipData.newRawUri(
            if (playlistUri != null) "Selection.jspf" else first.name, clipUris.first()).apply {
            clipUris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
        }
        setResult(result, data)
        finish()
    }

    private fun selectedItems(data: Intent): List<SelectionItem> {
        val uris = data.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_URIS)
            ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.orEmpty()
        val candidates = if (uris.isNotEmpty()) uris else buildList {
            data.data?.let(::add)
            data.getParcelableExtraCompat(Intent.EXTRA_STREAM)?.let(::add)
            data.clipData?.let { clip -> for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add) }
        }.distinct()
        val names = data.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_NAMES).orEmpty()
        val mimes = data.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_MIMES).orEmpty()
        val sizes = data.getLongArrayExtra(FileSelectionContract.EXTRA_RESULT_SIZES)
        val directoryUris = data.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_URIS).orEmpty()
        val directoryNames = data.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_NAMES).orEmpty()
        val directoryCoverUris = data.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_URIS).orEmpty()
        val sourceReferences = data.getStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_SOURCE_REFERENCES).orEmpty()
        val directoryReferences = data.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_REFERENCES).orEmpty()
        val directoryCoverReferences = data.getStringArrayListExtra(FileSelectionContract.EXTRA_ITEM_DIRECTORY_COVER_REFERENCES).orEmpty()
        return candidates.mapIndexed { index, uri ->
            val suppliedName = names.getOrNull(index)?.takeIf(String::isNotBlank)
            val suppliedMime = mimes.getOrNull(index)?.takeIf(String::isNotBlank)
            val hasSuppliedSize = sizes?.getOrNull(index) != null
            val suppliedSize = sizes?.getOrNull(index)?.takeIf { it >= 0L }
            val queried = if (suppliedName == null || suppliedMime == null || !hasSuppliedSize) runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (!cursor.moveToFirst()) null to null else {
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                            (nameColumn.takeIf { it >= 0 }?.let(cursor::getString)) to
                                (sizeColumn.takeIf { it >= 0 }?.let(cursor::getLong)?.takeIf { it >= 0L })
                        }
                    } ?: (null to null)
            }.getOrDefault(null to null) else null to null
            SelectionItem(uri,
                suppliedName ?: queried.first
                    ?: data.getStringExtra(FileSelectionContract.EXTRA_RESULT_NAME)
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file",
                suppliedMime
                    ?: contentResolver.getType(uri) ?: data.getStringExtra(FileSelectionContract.EXTRA_RESULT_MIME)
                    ?: "application/octet-stream",
                suppliedSize ?: queried.second,
                sourceReference = sourceReferences.getOrNull(index)?.takeIf(String::isNotBlank) ?: uri.toString(),
                directoryUri = directoryUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse),
                directoryName = directoryNames.getOrNull(index)?.takeIf(String::isNotBlank),
                directoryReference = directoryReferences.getOrNull(index)?.takeIf(String::isNotBlank)
                    ?: directoryUris.getOrNull(index)?.takeIf(String::isNotBlank),
                directoryCoverUri = directoryCoverUris.getOrNull(index)?.takeIf(String::isNotBlank)?.let(Uri::parse),
                directoryCoverReference = directoryCoverReferences.getOrNull(index)?.takeIf(String::isNotBlank)
                    ?: directoryCoverUris.getOrNull(index)?.takeIf(String::isNotBlank),
                directoryArtworkMetadataResolved = directoryUris.getOrNull(index)?.isNotBlank() == true)
        }
    }

    private fun finishWithCancel() { setResult(RESULT_CANCELED); finish() }

    private fun showMessage(message: String) {
        if (!inSourceChooser) return
        val view = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) as? ViewGroup ?: return
        view.addView(TextView(this).apply { text = message; setPadding(8, 12, 8, 12) })
    }

    private fun Intent.toRequest(): OpenRequest? {
        val clientId = getStringExtra(FileSelectionContract.EXTRA_CLIENT_ID) ?: return null
        return try { OpenRequest(
            clientId = clientId,
            displayName = getStringExtra(FileSelectionContract.EXTRA_DISPLAY_NAME) ?: applicationInfo.loadLabel(packageManager).toString(),
            mimeTypes = getStringArrayListExtra(FileSelectionContract.EXTRA_MIME_TYPES)?.takeIf { it.isNotEmpty() } ?: arrayListOf("*/*"),
            allowMultiple = getBooleanExtra(FileSelectionContract.EXTRA_ALLOW_MULTIPLE, false),
            initialDirectory = getParcelableExtraCompat(FileSelectionContract.EXTRA_INITIAL_DIRECTORY),
            supportedExtensions = getStringArrayListExtra(FileSelectionContract.EXTRA_SUPPORTED_EXTENSIONS).orEmpty(),
            hideUnsupported = getBooleanExtra(FileSelectionContract.EXTRA_HIDE_UNSUPPORTED, false),
            initialScrollY = getIntExtra(FileSelectionContract.EXTRA_INITIAL_SCROLL_Y, 0).coerceAtLeast(0),
            multipleSelectionDelivery = runCatching {
                MultipleSelectionDelivery.valueOf(getStringExtra(FileSelectionContract.EXTRA_MULTIPLE_DELIVERY)
                    ?: MultipleSelectionDelivery.ITEM_URIS.name)
            }.getOrDefault(MultipleSelectionDelivery.ITEM_URIS),
            metadataFormat = runCatching {
                SelectionMetadataFormat.valueOf(getStringExtra(FileSelectionContract.EXTRA_METADATA_FORMAT)
                    ?: SelectionMetadataFormat.NONE.name)
            }.getOrDefault(SelectionMetadataFormat.NONE),
            allowRecursiveAudioDirectories = getBooleanExtra(
                FileSelectionContract.EXTRA_ALLOW_RECURSIVE_AUDIO_DIRECTORIES, false),
            progressReceiver = getParcelableResultReceiver(FileSelectionContract.EXTRA_PROGRESS_RECEIVER),
        ) } catch (_: Exception) { null }
    }

    private fun Intent.toSaveRequest(): SaveRequest? {
        val clientId = getStringExtra(FileSelectionContract.EXTRA_CLIENT_ID) ?: return null
        val source = getParcelableExtraCompat(FileSelectionContract.EXTRA_SAVE_SOURCE_URI) ?: return null
        return runCatching { SaveRequest(
            clientId = clientId,
            displayName = getStringExtra(FileSelectionContract.EXTRA_DISPLAY_NAME) ?: applicationInfo.loadLabel(packageManager).toString(),
            fileName = getStringExtra(FileSelectionContract.EXTRA_SAVE_FILE_NAME) ?: return null,
            mimeType = getStringExtra(FileSelectionContract.EXTRA_SAVE_MIME_TYPE) ?: "application/octet-stream",
            sourceUri = source,
            sourceSizeBytes = getLongExtra(FileSelectionContract.EXTRA_SAVE_SOURCE_SIZE, -1L).takeIf { it >= 0L },
            initialDirectory = getParcelableExtraCompat(FileSelectionContract.EXTRA_INITIAL_DIRECTORY),
        ) }.getOrNull()
    }

    private fun Intent.getParcelableExtraCompat(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java)
        else @Suppress("DEPRECATION") (getParcelableExtra(key) as? Uri)

    private fun Intent.getParcelableResultReceiver(key: String): android.os.ResultReceiver? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, android.os.ResultReceiver::class.java)
        else @Suppress("DEPRECATION") (getParcelableExtra(key) as? android.os.ResultReceiver)

    companion object {
        const val ACTION_SAVE = "io.github.gretchycat.infiniselect.internal.SAVE"
        private const val INFINIFOLDER_PACKAGE = "com.infinifolder"
        private const val REQUEST_INFINIFOLDER = 4101
        private const val REQUEST_SAF = 4102
        private const val REQUEST_DEFAULT_DIRECTORY = 4103
        private const val REQUEST_BACKEND = 4104
        private const val REQUEST_INFINIFOLDER_SAVE = 4105
        private const val REQUEST_SAF_SAVE = 4106
    }
}
