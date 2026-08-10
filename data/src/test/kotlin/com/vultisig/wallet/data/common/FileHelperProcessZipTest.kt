package com.vultisig.wallet.data.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [Uri.processZip] — verifies that a `.zip` backup cannot exhaust the heap (issue #5562),
 * both per entry ([MAX_IMPORT_FILE_SIZE_BYTES]) and across the whole archive
 * ([MAX_IMPORT_ARCHIVE_CONTENT_BYTES]).
 */
internal class FileHelperProcessZipTest {

    private val uri: Uri = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    init {
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun `reads every allowed entry of a well formed archive`() = runTest {
        zipOf("vault-a.vult" to "content-a", "notes.png" to "ignored", "vault-b.bak" to "content-b")

        val result = uri.processZip(context)

        assertEquals(
            listOf(
                AppZipEntry("vault-a.vult", "content-a"),
                AppZipEntry("vault-b.bak", "content-b"),
            ),
            result,
        )
    }

    @Test
    fun `stops at an entry over the per entry limit`() = runTest {
        zipOf(
            "vault-a.vult" to "content-a",
            "huge.vult" to filler(MAX_IMPORT_FILE_SIZE_BYTES + 1),
            "vault-b.vult" to "content-b",
        )

        val result = uri.processZip(context)

        assertEquals(
            listOf(AppZipEntry("vault-a.vult", "content-a")),
            result,
            "extraction must stop instead of inflating the rest of an oversized entry",
        )
    }

    @Test
    fun `stops once the retained content exceeds the archive limit`() = runTest {
        val entrySize = 4L * 1024 * 1024 + 512 * 1024
        val entryCount = (MAX_IMPORT_ARCHIVE_CONTENT_BYTES / entrySize).toInt() + 1
        zipOf(*Array(entryCount + 1) { "vault-$it.vult" to filler(entrySize) })

        val result = uri.processZip(context)

        assertEquals(
            entryCount - 1,
            result.size,
            "the archive budget must stop extraction before the heap is exhausted",
        )
    }

    private fun filler(size: Long): String = "a".repeat(size.toInt())

    private fun zipOf(vararg entries: Pair<String, String>) {
        val bytes =
            ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { (name, content) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
        every { contentResolver.openInputStream(uri) } returns bytes.toByteArray().inputStream()
    }
}
