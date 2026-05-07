package com.vultisig.wallet.data.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for [Uri.fileContent] — verifies the fix for issue #4426 where transient
 * `FileNotFoundException` (EACCES) from the platform `ContentResolver` would crash the app.
 *
 * The function must return `null` (and log) on any I/O failure, including:
 * - `FileNotFoundException` with `EACCES` message (the original crash report)
 * - generic `IOException` while reading the stream
 * - `ContentResolver.openInputStream` returning `null`
 */
internal class FileHelperUriFileContentTest {

    private val uri: Uri = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    init {
        every { context.contentResolver } returns contentResolver
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
}
