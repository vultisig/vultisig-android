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
    context.contentResolver.openInputStream(this@processZip)?.use { inputStream ->
        ZipInputStream(inputStream).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
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
                                    "Skipping zip entry over the %d byte limit: %s",
                                    MAX_IMPORT_FILE_SIZE_BYTES,
                                    entryName,
                                )
                            } else {
                                val fileContent = bytes.toString(Charsets.UTF_8)
                                coroutineContext.ensureActive()
                                entries.add(AppZipEntry(entryName, fileContent))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: OutOfMemoryError) {
                            Timber.e(e, "Out of memory processing file: %s", entryName)
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing file: %s", entryName)
                        }
                    }
                }

                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }
        }
    } ?: run { Timber.w("Failed to open input stream for URI: $this") }
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
