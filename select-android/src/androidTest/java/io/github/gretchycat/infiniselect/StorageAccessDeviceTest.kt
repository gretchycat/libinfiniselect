package io.github.gretchycat.infiniselect

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

/** Runs against Android's actual resolver and descriptor APIs; compilation alone is not execution. */
@RunWith(AndroidJUnit4::class)
class StorageAccessDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun testSelectionDocumentCanBeReopenedAndSeekedWithoutStaging() {
        val bytes = byteArrayOf(11, 22, 33, 44)
        val uri = InfiniSelect.createDocument(context, "sample.bin", "application/octet-stream", bytes)
        StorageAccess.openRead(context, uri).use { assertTrue(bytes.contentEquals(it.readBytes())) }
        StorageAccess.openDescriptor(context, uri).use { descriptor ->
            assertEquals(StorageAccess.Support.SUPPORTED, StorageAccess.seekable(descriptor))
            android.system.Os.lseek(descriptor.fileDescriptor, 2, android.system.OsConstants.SEEK_SET)
            val result = ByteArray(1)
            assertEquals(1, android.system.Os.read(descriptor.fileDescriptor, result, 0, 1))
            assertEquals(33, result[0].toInt())
        }
    }
    @Test fun testDirectoryRequestUsesAndroidTreeGrantFlags() {
        val intent = StorageAccess.directoryIntent(context, writable = true)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    }
    @Test fun testTemporarySelectionIsNotPersisted() {
        val uri = InfiniSelect.createDocument(context, "temporary", "application/octet-stream", byteArrayOf(1))
        assertFalse(StorageAccess.persist(context, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    }

    @Test fun testLocalFileResolutionReturnsContentAccessAndPreservesFileIdentity() {
        val file = File(context.cacheDir, "authoritative-source.bin").apply {
            writeBytes(byteArrayOf(5, 13, 21, 34))
        }
        val authoritative = android.net.Uri.fromFile(file)
        val resolved = requireNotNull(InfiniSelect.resolveSource(context, authoritative.toString()))

        assertEquals("content", resolved.contentUri.scheme)
        assertEquals(authoritative, resolved.authoritativeUri)
        StorageAccess.openRead(context, resolved.contentUri).use {
            assertArrayEquals(byteArrayOf(5, 13, 21, 34), it.readBytes())
        }
        StorageAccess.openDescriptor(context, resolved.contentUri).use {
            assertEquals(StorageAccess.Support.SUPPORTED, StorageAccess.seekable(it))
        }
    }
}
