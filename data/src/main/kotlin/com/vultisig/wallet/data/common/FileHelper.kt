package com.vultisig.wallet.data.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns.DISPLAY_NAME
import android.provider.OpenableColumns.SIZE
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val DIRECTORY_NAME = "Vultisig"
private const val QRCODE_DIRECTORY_NAME = "QRCodes"
const val QRCODE_DIRECTORY_NAME_FULL = "$DIRECTORY_NAME/$QRCODE_DIRECTORY_NAME"

/**
 * Upper bound for importable backup content, in bytes.
 *
 * A vault backup (`.vult`/`.bak`/`.dat`, or a single entry of a `.zip` backup) is tens of KB, so 5
 * MB is roughly two orders of magnitude of headroom. The file picker accepts any URI the user
 * selects and the extension gate bounds nothing, so without this cap a large (or maliciously
 * crafted) file is read wholesale into the heap and kills the process with an [OutOfMemoryError].
 */
internal const val MAX_IMPORT_FILE_SIZE_BYTES = 5L * 1024 * 1024

/**
 * Upper bound for the content decompressed from a single `.zip` backup, in bytes.
 *
 * [MAX_IMPORT_FILE_SIZE_BYTES] only bounds one entry, so an archive of many individually valid
 * entries can still exhaust the heap. Entries whose extension is not importable are skipped rather
 * than retained, but skipping still inflates them, so they count towards this budget too. A backup
 * zip holds a handful of vault shares, so 20 MB leaves ample headroom for legitimate archives.
 */
internal const val MAX_IMPORT_ARCHIVE_CONTENT_BYTES = 20L * 1024 * 1024

/**
 * Upper bound for the number of entries traversed in a single `.zip` backup.
 *
 * Empty entries add nothing to the byte budgets, so an archive of many zero-byte entries stays
 * under both limits while still growing the returned list and the traversal time. A backup zip
 * holds a handful of vault shares, so 1000 entries leaves ample headroom.
 */
internal const val MAX_IMPORT_ARCHIVE_ENTRIES = 1000

suspend fun Context.saveContentToUri(uri: Uri, content: String) = doFileOperation {
    try {
        contentResolver.openOutputStream(uri).use { output ->
            content.byteInputStream().use {
                it.copyTo(
                    output ?: error("FileHelper::saveContentToUri output is null"),
                    DEFAULT_BUFFER_SIZE,
                )
            }
            return@doFileOperation true
        }
    } catch (e: Exception) {
        Timber.e(e, message = "error in saveContentToUri")
        return@doFileOperation false
    }
}

suspend fun Context.saveContentToUri(uri: Uri, contentList: List<AppZipEntry>): Boolean =
    doFileOperation {
        if (contentList.isEmpty()) {
            Timber.w("Refusing to write a ZIP backup with no entries")
            return@doFileOperation false
        }
        try {
            contentResolver.openOutputStream(uri).use { outputStream ->
                ZipOutputStream(outputStream).use { zipOutputStream ->
                    contentList.forEach { content ->
                        val zipEntry = ZipEntry(content.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        content.content.byteInputStream().use { input ->
                            input.copyTo(zipOutputStream, DEFAULT_BUFFER_SIZE)
                        }
                        zipOutputStream.closeEntry()
                    }
                }
            }
            return@doFileOperation true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save ZIP content to URI")
            return@doFileOperation false
        }
    }

suspend fun Context.saveBitmapToDownloads(bitmap: Bitmap, fileName: String): Uri? {
    return doFileOperation {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBitmapToDownloadsDirAtLeastQ(bitmap, fileName)
        } else {
            saveBitmapToDownloadsDirLegacy(bitmap, fileName)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun Context.saveBitmapToDownloadsDirAtLeastQ(
    bitmap: Bitmap,
    fileName: String,
): Uri? = doFileOperation {
    val contentValues =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/$QRCODE_DIRECTORY_NAME_FULL",
            )
        }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@doFileOperation null

    return@doFileOperation resolver.openOutputStream(downloadUri).use { bitmapStream ->
        if (bitmapStream != null) {
            bitmap.compressPng(bitmapStream)
            bitmap.recycle()
            downloadUri
        } else {
            return@doFileOperation null
        }
    }
}

internal suspend fun Context.saveBitmapToDownloadsDirLegacy(
    bitmap: Bitmap,
    fileName: String,
): Uri? = doFileOperation {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath + "/$QRCODE_DIRECTORY_NAME_FULL"
            )
        return@doFileOperation try {
            if (!downloadsDirectory.exists()) {
                downloadsDirectory.mkdirs()
            }
            val file = File(downloadsDirectory, fileName)
            FileOutputStream(file).use { bitmap.compressPng(it) }
            bitmap.recycle()
            provideFileUri(file)
        } catch (e: Exception) {
            Timber.e(e, message = "error in saveBitmapToDownloadsDirLegacy")
            null
        }
    }
    return@doFileOperation null
}

suspend fun Context.provideFileUri(file: File): Uri = doFileOperation {
    FileProvider.getUriForFile(this@provideFileUri, "$packageName.provider", file)
}

suspend fun Uri.fileContent(context: Context): String? = doFileOperation {
    try {
        val reportedSize = reportedSize(context)
        if (reportedSize != null && reportedSize > MAX_IMPORT_FILE_SIZE_BYTES) {
            Timber.w(
                "Refusing to read file of %d bytes, limit is %d",
                reportedSize,
                MAX_IMPORT_FILE_SIZE_BYTES,
            )
            return@doFileOperation null
        }
        context.contentResolver.openInputStream(this@fileContent)?.use { input ->
            val bytes = input.readBounded(MAX_IMPORT_FILE_SIZE_BYTES)
            if (bytes == null) {
                Timber.w("Refusing to read file over the %d byte limit", MAX_IMPORT_FILE_SIZE_BYTES)
                null
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        Timber.e(e, "out of memory in fileContent")
        null
    } catch (e: Exception) {
        Timber.e(e, "error in fileContent")
        null
    }
}

/**
 * Reads the size this URI's provider reports via [SIZE].
 *
 * @return the size in bytes, or `null` when the provider does not expose it — callers must still
 *   bound the read itself, since the reported value is neither guaranteed nor trustworthy.
 */
private fun Uri.reportedSize(context: Context): Long? =
    try {
        context.contentResolver.query(this, arrayOf(SIZE), null, null, null)?.use { cursor ->
            val sizeColumnIndex = cursor.getColumnIndex(SIZE)
            if (sizeColumnIndex < 0 || !cursor.moveToFirst() || cursor.isNull(sizeColumnIndex)) {
                null
            } else {
                cursor.getLong(sizeColumnIndex).takeIf { it >= 0 }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "unable to query size, falling back to a bounded read")
        null
    }

/**
 * Reads this stream fully while refusing to allocate more than [maxBytes].
 *
 * @return the bytes read, or `null` as soon as the stream is known to exceed [maxBytes].
 */
private fun InputStream.readBounded(maxBytes: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(chunk)
        if (count == -1) break
        total += count
        if (total > maxBytes) return null
        buffer.write(chunk, 0, count)
    }
    return buffer.toByteArray()
}

/**
 * Discards the rest of the current zip entry while refusing to inflate more than [maxBytes].
 *
 * @return the number of bytes skipped, or `null` as soon as the entry is known to exceed
 *   [maxBytes].
 */
private fun ZipInputStream.skipBounded(maxBytes: Long): Long? {
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(chunk)
        if (count == -1) return total
        total += count
        if (total > maxBytes) return null
    }
}

suspend fun Uri.fileName(context: Context): String? = doFileOperation {
    retryWithDelay {
        val cursor =
            context.contentResolver.query(this@fileName, arrayOf(DISPLAY_NAME), null, null, null)

        cursor?.use {
            val nameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameColumnIndex)
        }
    }
}

internal suspend fun Bitmap.compressPng(stream: OutputStream) = doFileOperation {
    compress(Bitmap.CompressFormat.PNG, 100, stream)
}

suspend fun Uri.processZip(context: Context): List<AppZipEntry> = doFileOperation {
    val entries = mutableListOf<AppZipEntry>()
    try {
        context.contentResolver.openInputStream(this@processZip)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var zipEntry = zipInputStream.nextEntry
                var inflatedBytes = 0L
                var entryCount = 0
                entryLoop@ while (zipEntry != null) {
                    entryCount++
                    if (entryCount > MAX_IMPORT_ARCHIVE_ENTRIES) {
                        Timber.w(
                            "Stopping, the archive holds more than %d entries",
                            MAX_IMPORT_ARCHIVE_ENTRIES,
                        )
                        break@entryLoop
                    }
                    if (!zipEntry.isDirectory) {
                        val entryName = zipEntry.name
                        val ext = File(entryName).extension.lowercase()
                        if (FILE_ALLOWED_EXTENSIONS.contains(ext)) {
                            try {
                                // The declared entry size in the zip header is untrusted, so bound
                                // against the bytes actually decompressed.
                                val bytes = zipInputStream.readBounded(MAX_IMPORT_FILE_SIZE_BYTES)
                                if (bytes == null) {
                                    Timber.w(
                                        "Stopping at a zip entry over the %d byte limit: %s",
                                        MAX_IMPORT_FILE_SIZE_BYTES,
                                        entryName,
                                    )
                                    // Advancing to the next entry inflates whatever is left of this
                                    // one, so leave the archive alone instead of paying that cost.
                                    break@entryLoop
                                }
                                inflatedBytes += bytes.size
                                if (inflatedBytes > MAX_IMPORT_ARCHIVE_CONTENT_BYTES) {
                                    Timber.w(
                                        "Stopping at %s, the archive exceeds the %d byte limit",
                                        entryName,
                                        MAX_IMPORT_ARCHIVE_CONTENT_BYTES,
                                    )
                                    break@entryLoop
                                }
                                val fileContent = bytes.toString(Charsets.UTF_8)
                                coroutineContext.ensureActive()
                                entries.add(AppZipEntry(entryName, fileContent))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: OutOfMemoryError) {
                                Timber.e(e, "Out of memory processing file: %s", entryName)
                                // Advancing past a half-read entry inflates the rest of it, so stop
                                // here rather than pay that cost after a failure.
                                break@entryLoop
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing file: %s", entryName)
                                break@entryLoop
                            }
                        } else {
                            // Skipping an entry still inflates it, so a disallowed extension is no
                            // protection against a zip bomb — bound that read as well.
                            val skipped = zipInputStream.skipBounded(MAX_IMPORT_FILE_SIZE_BYTES)
                            if (skipped == null) {
                                Timber.w(
                                    "Stopping at a skipped zip entry over the %d byte limit: %s",
                                    MAX_IMPORT_FILE_SIZE_BYTES,
                                    entryName,
                                )
                                break@entryLoop
                            }
                            inflatedBytes += skipped
                            if (inflatedBytes > MAX_IMPORT_ARCHIVE_CONTENT_BYTES) {
                                Timber.w(
                                    "Stopping at %s, the archive exceeds the %d byte limit",
                                    entryName,
                                    MAX_IMPORT_ARCHIVE_CONTENT_BYTES,
                                )
                                break@entryLoop
                            }
                        }
                    }

                    // nextEntry closes the current entry itself; calling closeEntry() first would
                    // only repeat the work.
                    zipEntry = zipInputStream.nextEntry
                }
            }
        } ?: run { Timber.w("Failed to open input stream for URI: $this") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // A corrupt or truncated archive throws while advancing between entries, outside the
        // per-entry guard, and would otherwise crash the import.
        Timber.e(e, "Out of memory reading zip: %s", this@processZip)
    } catch (e: Exception) {
        Timber.e(e, "Error reading zip: %s", this@processZip)
    }
    return@doFileOperation entries
}

suspend fun Uri.isValidZipFile(context: Context) = doFileOperation {
    try {
        val hasZipExtension =
            File(fileName(context) ?: return@doFileOperation false)
                .extension
                .equals("zip", ignoreCase = true)
        hasZipExtension &&
            context.contentResolver.openInputStream(this@isValidZipFile)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream -> zipStream.nextEntry != null }
            } ?: false
    } catch (_: Exception) {
        false
    }
}

suspend fun Context.deleteDocument(uri: Uri): Boolean = doFileOperation {
    try {
        DocumentsContract.deleteDocument(contentResolver, uri)
    } catch (e: Exception) {
        Timber.e(e, "Failed to delete document: %s", uri)
        false
    }
}

private suspend fun <T> doFileOperation(block: suspend CoroutineScope.() -> T) =
    withContext(context = Dispatchers.IO, block = block)

private suspend inline fun <T> retryWithDelay(
    attempts: Int = 3,
    delay: Long = 100,
    defaultValue: T? = null,
    block: () -> T,
): T? {
    repeat(attempts - 1) { attempt ->
        try {
            return block()
        } catch (_: Throwable) {
            Timber.e("Error in retryWithDelay, attempt: $attempt")
        }
        delay(delay.milliseconds * (attempt + 1))
    }

    return try {
        block()
    } catch (_: Throwable) {
        Timber.e("Error in retryWithDelay, show default value '$defaultValue'")
        defaultValue
    }
}
