package io.github.gretchycat.infiniselect

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.Intent

/** Request parameters for one viewer's file-open action. */
data class OpenRequest(
    val clientId: String,
    val displayName: String,
    val mimeTypes: List<String> = listOf("*/*"),
    val allowMultiple: Boolean = false,
    val initialDirectory: Uri? = null,
) {
    init {
        require(clientId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "clientId must be a short stable identifier" }
        require(mimeTypes.isNotEmpty()) { "At least one MIME type is required" }
    }

    internal fun toBundle() = Bundle().apply {
        putString(FileSelectionContract.EXTRA_CLIENT_ID, clientId)
        putString(FileSelectionContract.EXTRA_DISPLAY_NAME, displayName)
        putStringArrayList(FileSelectionContract.EXTRA_MIME_TYPES, ArrayList(mimeTypes))
        putBoolean(FileSelectionContract.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        initialDirectory?.let { putParcelable(FileSelectionContract.EXTRA_INITIAL_DIRECTORY, it) }
    }
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
