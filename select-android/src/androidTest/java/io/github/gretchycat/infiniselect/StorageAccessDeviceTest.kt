package io.github.gretchycat.infiniselect

import android.content.Intent
import android.test.AndroidTestCase

/** Runs against Android's actual resolver and descriptor APIs; compilation alone is not execution. */
@Suppress("DEPRECATION")
class StorageAccessDeviceTest : AndroidTestCase() {
    fun testSelectionDocumentCanBeReopenedAndSeekedWithoutStaging() {
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
    fun testDirectoryRequestUsesAndroidTreeGrantFlags() {
        val intent = StorageAccess.directoryIntent(context, writable = true)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    }
    fun testTemporarySelectionIsNotPersisted() {
        val uri = InfiniSelect.createDocument(context, "temporary", "application/octet-stream", byteArrayOf(1))
        assertFalse(StorageAccess.persist(context, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    }
}
