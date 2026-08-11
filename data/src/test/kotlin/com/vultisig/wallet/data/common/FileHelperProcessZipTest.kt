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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [Uri.processZip] — verifies that a `.zip` backup cannot exhaust the heap (issue #5562),
 * both per entry ([MAX_IMPORT_FILE_SIZE_BYTES]) and across the whole archive
 * ([MAX_IMPORT_ARCHIVE_CONTENT_BYTES]).
 */
internal class FileHelperProcessZipTest {

    private companion object {
        /** Size of a zip local file header, before the variable length name and extra fields. */
        const val LOCAL_HEADER_SIZE = 30
    }

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
            result.entries,
        )
        assertTrue(result.isComplete, "a fully traversed archive must report a complete extraction")
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
            result.entries,
            "extraction must stop instead of inflating the rest of an oversized entry",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
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
            result.entries.size,
            "the archive budget must stop extraction before the heap is exhausted",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `stops at a disallowed entry over the per entry limit`() = runTest {
        zipOf(
            "vault-a.vult" to "content-a",
            "huge.png" to filler(MAX_IMPORT_FILE_SIZE_BYTES + 1),
            "vault-b.vult" to "content-b",
        )

        val result = uri.processZip(context)

        assertEquals(
            listOf(AppZipEntry("vault-a.vult", "content-a")),
            result.entries,
            "skipping a disallowed entry must not inflate it past the limit either",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `stops at a directory named entry over the per entry limit`() = runTest {
        zipOf(
            "vault-a.vult" to "content-a",
            "evil/" to filler(MAX_IMPORT_FILE_SIZE_BYTES + 1),
            "vault-b.vult" to "content-b",
        )

        val result = uri.processZip(context)

        assertEquals(
            listOf(AppZipEntry("vault-a.vult", "content-a")),
            result.entries,
            "isDirectory is name only, so a directory named entry with a payload must be bounded too",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `returns the entries read so far when the archive is truncated`() = runTest {
        val bytes = zipBytes("vault-a.vult" to "content-a", "vault-b.vult" to "content-b")
        every { contentResolver.openInputStream(uri) } returns
            bytes.truncatedInsideSecondEntryHeader().inputStream()

        val result = uri.processZip(context)

        assertEquals(
            listOf(AppZipEntry("vault-a.vult", "content-a")),
            result.entries,
            "a truncated archive must not propagate a ZipException to the caller",
        )
        assertFalse(
            result.isComplete,
            "a truncated archive must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `stops once the archive holds too many entries`() = runTest {
        zipOf(*Array(MAX_IMPORT_ARCHIVE_ENTRIES + 5) { "vault-$it.vult" to "" })

        val result = uri.processZip(context)

        assertEquals(
            MAX_IMPORT_ARCHIVE_ENTRIES,
            result.entries.size,
            "empty entries add nothing to the byte budgets, so the entry count must bound them",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `counts skipped entries towards the entry limit`() = runTest {
        val skipped = Array(MAX_IMPORT_ARCHIVE_ENTRIES) { "notes-$it.png" to "" }
        zipOf(*skipped, "vault-a.vult" to "content-a")

        val result = uri.processZip(context)

        assertEquals(
            emptyList<AppZipEntry>(),
            result.entries,
            "entries of a disallowed extension must count towards the entry limit too",
        )
        assertFalse(
            result.isComplete,
            "stopping early must be reported as an incomplete extraction",
        )
    }

    @Test
    fun `returns the entries read so far when an entry read fails`() = runTest {
        val bytes = zipBytes("vault-a.vult" to "content-a", "vault-b.vult" to filler(4096L))
        every { contentResolver.openInputStream(uri) } returns
            bytes.truncatedInsideSecondEntryData().inputStream()

        val result = uri.processZip(context)

        assertEquals(
            listOf(AppZipEntry("vault-a.vult", "content-a")),
            result.entries,
            "a failed entry read must stop extraction instead of inflating the rest of the entry",
        )
        assertFalse(
            result.isComplete,
            "a failed entry read must be reported as an incomplete extraction",
        )
    }

    private fun filler(size: Long): String = "a".repeat(size.toInt())

    /**
     * Cuts the archive off part way through the second entry's local file header, the point at
     * which advancing to that entry throws instead of reporting a clean end of archive.
     */
    private fun ByteArray.truncatedInsideSecondEntryHeader(): ByteArray =
        copyOf(secondLocalHeaderIndex() + LOCAL_HEADER_SIZE + 4)

    /**
     * Cuts the archive off a couple of bytes into the second entry's deflated data, the point at
     * which reading that entry throws part way through it.
     */
    private fun ByteArray.truncatedInsideSecondEntryData(): ByteArray {
        val header = secondLocalHeaderIndex()
        val nameLength = readShortLe(header + 26)
        val extraLength = readShortLe(header + 28)
        return copyOf(header + LOCAL_HEADER_SIZE + nameLength + extraLength + 2)
    }

    private fun ByteArray.readShortLe(index: Int): Int =
        (this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.secondLocalHeaderIndex(): Int {
        val signature = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        var seen = 0
        for (index in 0..size - signature.size) {
            if (signature.indices.all { this[index + it] == signature[it] }) {
                seen++
                if (seen == 2) return index
            }
        }
        error("second local file header not found")
    }

    private fun zipOf(vararg entries: Pair<String, String>) {
        every { contentResolver.openInputStream(uri) } returns zipBytes(*entries).inputStream()
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { (name, content) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
            .toByteArray()
}
