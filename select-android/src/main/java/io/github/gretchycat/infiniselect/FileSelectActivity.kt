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
        request = intent.toRequest() ?: run { finish(); return }
        startPreferredSource()
    }

    private fun startPreferredSource() {
        if (isInfiniFolderInstalled()) {
            val selection = Intent(FileSelectionContract.ACTION_SELECT)
                .setPackage(INFINIFOLDER_PACKAGE)
                .putExtras(request.toBundle())
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
            request.initialDirectory?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { startActivityForResult(picker, REQUEST_SAF) }
            .onFailure { showMessage("Android's file picker is unavailable: ${it.message ?: "no picker app"}") }
    }

    private fun launchDefaultFolderPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            request.initialDirectory?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
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
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getBooleanExtra(FileSelectionContract.EXTRA_FALLBACK_TO_SOURCES, false)) {
                        sourceMessage = data.getStringExtra(FileSelectionContract.EXTRA_FAILURE_MESSAGE)
                        showSourceChooser()
                    } else returnToCaller(resultCode, data)
                } else finishWithCancel()
            }
            REQUEST_SAF, REQUEST_BACKEND -> if (resultCode == RESULT_OK && data != null) {
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
        }
    }

    private fun retainGrant(data: Intent) {
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uris = linkedSetOf<Uri>()
        data.data?.let(uris::add)
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
        }
        uris.forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, flags) } }
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
        val primaryMime = if (playlistUri != null) InfiniSelect.JSPF_MIME else first.mimeType
        data.setDataAndType(primary, primaryMime)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_URI, first.uri.toString())
        data.putExtra(FileSelectionContract.EXTRA_RESULT_NAME, first.name)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_MIME, first.mimeType)
        data.putExtra(FileSelectionContract.EXTRA_RESULT_SIZE, first.sizeBytes ?: -1L)
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_URIS, ArrayList(items.map { it.uri.toString() }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_NAMES, ArrayList(items.map { it.name }))
        data.putStringArrayListExtra(FileSelectionContract.EXTRA_RESULT_MIMES, ArrayList(items.map { it.mimeType }))
        data.putExtra(FileSelectionContract.EXTRA_RESULT_SIZES, items.map { it.sizeBytes ?: -1L }.toLongArray())
        playlistUri?.let { data.putExtra(FileSelectionContract.EXTRA_RESULT_PLAYLIST_URI, it.toString()) }
        metadataUri?.let { data.putExtra(FileSelectionContract.EXTRA_RESULT_METADATA_URI, it.toString()) }
        data.putExtra("displayName", if (playlistUri != null) "Selection.jspf" else first.name)
        data.putExtra("mimeType", primaryMime)
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        data.clipData = android.content.ClipData.newRawUri(first.name, first.uri).apply {
            items.drop(1).forEach { addItem(android.content.ClipData.Item(it.uri)) }
            metadataUri?.let { addItem(android.content.ClipData.Item(it)) }
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
        return candidates.mapIndexed { index, uri ->
            val queried = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (!cursor.moveToFirst()) null to null else {
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                            (nameColumn.takeIf { it >= 0 }?.let(cursor::getString)) to
                                (sizeColumn.takeIf { it >= 0 }?.let(cursor::getLong)?.takeIf { it >= 0L })
                        }
                    } ?: (null to null)
            }.getOrDefault(null to null)
            SelectionItem(uri,
                names.getOrNull(index)?.takeIf(String::isNotBlank) ?: queried.first
                    ?: data.getStringExtra(FileSelectionContract.EXTRA_RESULT_NAME)
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file",
                mimes.getOrNull(index)?.takeIf(String::isNotBlank)
                    ?: contentResolver.getType(uri) ?: data.getStringExtra(FileSelectionContract.EXTRA_RESULT_MIME)
                    ?: "application/octet-stream",
                sizes?.getOrNull(index)?.takeIf { it >= 0L } ?: queried.second)
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
        ) } catch (_: Exception) { null }
    }

    private fun Intent.getParcelableExtraCompat(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java)
        else @Suppress("DEPRECATION") (getParcelableExtra(key) as? Uri)

    companion object {
        private const val INFINIFOLDER_PACKAGE = "com.infinifolder"
        private const val REQUEST_INFINIFOLDER = 4101
        private const val REQUEST_SAF = 4102
        private const val REQUEST_DEFAULT_DIRECTORY = 4103
        private const val REQUEST_BACKEND = 4104
    }
}
