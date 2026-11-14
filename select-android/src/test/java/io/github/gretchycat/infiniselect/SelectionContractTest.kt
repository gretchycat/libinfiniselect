package io.github.gretchycat.infiniselect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionContractTest {
    @Test
    fun currentGrantedRelayStaysTheAccessUriAndCanonicalContentUriStaysIdentity() {
        val relay = "content://com.infinifolder.selection/entry-1"
        val canonical = "content://com.termux.documents/tree/home/document/home%2Ftrack.flac"

        val resolved = selectCurrentAccessLocation(relay, canonical, hasReadAccess = true)

        assertEquals(SelectedSourceLocations(relay, canonical), resolved)
    }

    @Test
    fun inaccessibleCurrentLinkRequiresAResolutionRequest() {
        val result = selectCurrentAccessLocation(
            "content://com.infinifolder.selection/expired",
            "sftp://192.168.1.25/Music/track.flac",
            hasReadAccess = false,
        )

        assertNull(result)
    }

    @Test
    fun stableAndroidDocumentCanBeBothCurrentAccessAndIdentity() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Ftrack.flac"

        assertEquals(SelectedSourceLocations(uri, uri),
            selectCurrentAccessLocation(uri, null, hasReadAccess = true))
    }

    @Test
    fun androidGrantsContainAccessLinksButNeverAuthoritativeReferences() {
        val result = SelectionGrantPolicy.collectUris(
            primary = GrantableSelectionUri("content://app.selection/selection.jspf"),
            items = listOf(GrantableSelectionUri(
                "content://app.selection/track-1",
                "content://com.termux.documents/tree/home/document/home%2Ftrack.flac",
            )),
            directories = listOf(GrantableSelectionUri(
                "content://com.infinifolder.documents/document/music",
                "content://com.termux.documents/tree/home/document/home%2FMusic",
            )),
            covers = listOf(GrantableSelectionUri("content://app.selection/cover-1", "sftp://host/Music/cover.jpg")),
            metadata = GrantableSelectionUri("content://app.selection/metadata.xml"),
        )

        assertEquals(listOf(
            "content://app.selection/selection.jspf",
            "content://app.selection/track-1",
            "content://com.infinifolder.documents/document/music",
            "content://app.selection/cover-1",
            "content://app.selection/metadata.xml",
        ), result)
    }
}
