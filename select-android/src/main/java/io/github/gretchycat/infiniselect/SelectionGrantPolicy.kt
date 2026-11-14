package io.github.gretchycat.infiniselect

/** An Android access link paired with its backend-neutral, durable identity. */
internal data class GrantableSelectionUri(
    val contentUri: String?,
    val authoritativeUri: String? = null,
)

/** Only access links belong in Android URI grants and ClipData. */
internal object SelectionGrantPolicy {
    fun collectUris(
        primary: GrantableSelectionUri?,
        items: List<GrantableSelectionUri>,
        directories: List<GrantableSelectionUri>,
        covers: List<GrantableSelectionUri>,
        metadata: GrantableSelectionUri?,
        grantableAccessUris: Set<String>,
    ): List<String> = buildList {
        primary?.contentUri?.let(::add)
        items.mapNotNullTo(this) { it.contentUri }
        directories.mapNotNullTo(this) { it.contentUri }
        covers.mapNotNullTo(this) { it.contentUri }
        metadata?.contentUri?.let(::add)
    }.filter { value ->
        value.substringBefore(':', "").equals("content", ignoreCase = true) &&
            value in grantableAccessUris
    }.distinct()
}
