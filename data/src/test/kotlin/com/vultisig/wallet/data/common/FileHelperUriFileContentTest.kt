package com.vultisig.wallet.data.common

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns.SIZE
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests for [Uri.fileContent] — verifies the fix for issue #4426 where transient
 * `FileNotFoundException` (EACCES) from the platform `ContentResolver` would crash the app.
 *
 * The function must return `null` (and log) on any I/O failure, including:
 * - `FileNotFoundException` with `EACCES` message (the original crash report)
 * - generic `IOException` while reading the stream
 * - `ContentResolver.openInputStream` returning `null`
 *
 * It must also refuse files above [MAX_IMPORT_FILE_SIZE_BYTES] rather than crashing with an
 * `OutOfMemoryError` (issue #5562), both when the provider reports an over-limit `SIZE` and when it
 * reports no usable size at all.
 */
internal class FileHelperUriFileContentTest {

    private val uri: Uri = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    init {
        every { context.contentResolver } returns contentResolver
        // Default: the provider exposes no usable SIZE, so only the bounded read guards the heap.
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns null
    }

    @Test
    fun `returns UTF-8 file contents on successful read`() = runTest {
        val payload = "vultisig-vault-payload"
        every { contentResolver.openInputStream(uri) } returns
            ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))

        val result = uri.fileContent(context)

        assertEquals(payload, result)
    }

    @Test
    fun `returns null when openInputStream throws FileNotFoundException with EACCES`() = runTest {
        every { contentResolver.openInputStream(uri) } throws
            FileNotFoundException(
                "/storage/emulated/0/Download/share1of2.bak: open failed: EACCES (Permission denied)"
            )

        val result = uri.fileContent(context)

        assertNull(result, "EACCES on openInputStream must be caught, not propagated")
    }

    @Test
    fun `returns null when openInputStream throws generic FileNotFoundException`() = runTest {
        every { contentResolver.openInputStream(uri) } throws FileNotFoundException("missing")

        val result = uri.fileContent(context)

        assertNull(result)
    }

    @Test
    fun `returns null when openInputStream throws SecurityException`() = runTest {
        every { contentResolver.openInputStream(uri) } throws SecurityException("Permission denial")

        val result = uri.fileContent(context)

        assertNull(result)
    }

    @Test
    fun `returns null when InputStream throws IOException during read`() = runTest {
        val failing =
            object : InputStream() {
                override fun read(): Int = throw IOException("disk read failed")
            }
        every { contentResolver.openInputStream(uri) } returns failing

        val result = uri.fileContent(context)

        assertNull(result)
    }

    @Test
    fun `returns null when openInputStream returns null`() = runTest {
        every { contentResolver.openInputStream(uri) } returns null

        val result = uri.fileContent(context)

        assertNull(result)
    }

    @Test
    fun `does not swallow CancellationException`() {
        every { contentResolver.openInputStream(uri) } throws CancellationException("scope cleared")

        assertThrows(CancellationException::class.java) { runBlocking { uri.fileContent(context) } }
    }

    @Test
    fun `returns null without opening the stream when reported size exceeds the limit`() = runTest {
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns
            sizeCursor(MAX_IMPORT_FILE_SIZE_BYTES + 1)

        val result = uri.fileContent(context)

        assertNull(result, "an over-limit file must be rejected before it is allocated")
        verify(exactly = 0) { contentResolver.openInputStream(uri) }
    }

    @Test
    fun `returns contents when reported size is within the limit`() = runTest {
        val payload = "vultisig-vault-payload"
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns
            sizeCursor(payload.length.toLong())
        every { contentResolver.openInputStream(uri) } returns
            ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))

        val result = uri.fileContent(context)

        assertEquals(payload, result)
    }

    @Test
    fun `returns null when size is unknown but the stream exceeds the limit`() = runTest {
        every { contentResolver.openInputStream(uri) } returns
            oversizedStream(MAX_IMPORT_FILE_SIZE_BYTES + 1)

        val result = uri.fileContent(context)

        assertNull(result, "the read itself must be capped when the provider reports no SIZE")
    }

    @Test
    fun `returns contents when size is unknown and the stream is within the limit`() = runTest {
        val payload = "a".repeat(64 * 1024)
        every { contentResolver.openInputStream(uri) } returns
            ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))

        val result = uri.fileContent(context)

        assertEquals(payload, result)
    }

    @Test
    fun `returns null when the stream throws OutOfMemoryError`() = runTest {
        val failing =
            object : InputStream() {
                override fun read(): Int = throw OutOfMemoryError("Failed to allocate")
            }
        every { contentResolver.openInputStream(uri) } returns failing

        val result = uri.fileContent(context)

        assertNull(result, "OutOfMemoryError is an Error, it must still be caught here")
    }

    private fun sizeCursor(size: Long): Cursor =
        mockk<Cursor>(relaxed = true).also { cursor ->
            every { cursor.getColumnIndex(SIZE) } returns 0
            every { cursor.moveToFirst() } returns true
            every { cursor.isNull(0) } returns false
            every { cursor.getLong(0) } returns size
        }

    private fun oversizedStream(sizeBytes: Long): InputStream =
        object : InputStream() {
            private var remaining = sizeBytes

            override fun read(): Int = if (remaining-- > 0) FILLER.toInt() else -1

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len.toLong(), remaining).toInt()
                b.fill(FILLER, off, off + count)
                remaining -= count
                return count
            }
        }

    private companion object {
        val FILLER: Byte = 'a'.code.toByte()
    }
}
