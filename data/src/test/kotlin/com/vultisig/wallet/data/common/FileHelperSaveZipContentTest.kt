package com.vultisig.wallet.data.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [Context.saveContentToUri] (the `List<AppZipEntry>` / ZIP overload) — verifies the fix
 * for issue #5173, where a zero-entry list produced a broken ZIP that was still reported as a
 * successful backup.
 */
internal class FileHelperSaveZipContentTest {

    private val uri: Uri = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    init {
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun `returns false and never opens an output stream for an empty entry list`() = runTest {
        val result = context.saveContentToUri(uri, emptyList())

        assertFalse(result)
        verify(exactly = 0) { contentResolver.openOutputStream(any()) }
    }

    @Test
    fun `writes every entry into the zip and returns true`() = runTest {
        val output = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns output
        val entries =
            listOf(
                AppZipEntry("vault-a.vult", "content-a"),
                AppZipEntry("vault-b.vult", "content-b"),
            )

        val result = context.saveContentToUri(uri, entries)

        assertTrue(result)
        val readBack = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                readBack[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertEquals(mapOf("vault-a.vult" to "content-a", "vault-b.vult" to "content-b"), readBack)
    }
}
