package com.vultisig.wallet.data.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns.DISPLAY_NAME
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DIRECTORY_NAME = "Vultisig"
private const val QRCODE_DIRECTORY_NAME = "QRCodes"
private const val BACKUPS_DIRECTORY_NAME = "Backups"
const val QRCODE_DIRECTORY_NAME_FULL = "$DIRECTORY_NAME/$QRCODE_DIRECTORY_NAME"
internal const val BACKUPS_DIRECTORY_NAME_FULL = "$DIRECTORY_NAME/$BACKUPS_DIRECTORY_NAME"


@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.backupVaultToDownloadsDirAtLeastQ(json: String, backupFileName: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$BACKUPS_DIRECTORY_NAME_FULL"
        )
    }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false

    try {
        resolver.openOutputStream(downloadUri).use { output ->
            json.byteInputStream()
                .use {
                    it.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            return true
        }
    } catch (e: Exception) {
        return false
    }
}

fun Context.saveContentToUri(uri: Uri, content: String): Boolean {
    try {
        contentResolver.openOutputStream(uri).use { output ->
            content.byteInputStream()
                .use {
                    it.copyTo(
                        output ?: error("FileHelper::saveContentToUri output is null"),
                        DEFAULT_BUFFER_SIZE
                    )
                }
            return true
        }
    } catch (e: Exception) {
        return false
    }
}

fun Context.saveContentToUri(uri: Uri, contentList: List<AppZipEntry>): Boolean {
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
        return true
    } catch (e: Exception) {
        Timber.e(e, "Failed to save ZIP content to URI")
        return false
    }
}


internal fun backupVaultToDownloadsDir(json: String, backupFileName: String): Boolean {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath + "/$BACKUPS_DIRECTORY_NAME_FULL"
        )

        try {
            if (!downloadsDirectory.exists()) {
                downloadsDirectory.mkdirs()
            }
            val jsonFile = File(downloadsDirectory, backupFileName)
            FileWriter(jsonFile).use { fileWriter ->
                fileWriter.write(json)
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }
    return false
}

fun Context.saveBitmapToDownloads(bitmap: Bitmap, fileName: String): Uri?{
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveBitmapToDownloadsDirAtLeastQ(bitmap, fileName)
    } else {
        saveBitmapToDownloadsDirLegacy(bitmap, fileName)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.saveBitmapToDownloadsDirAtLeastQ(bitmap: Bitmap, fileName: String): Uri? {
    val contentValues = ContentValues().apply {
        put(
            MediaStore.MediaColumns.DISPLAY_NAME,
            fileName
        )
        put(
            MediaStore.MediaColumns.MIME_TYPE,
            "image/png"
        )
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$QRCODE_DIRECTORY_NAME_FULL"
        )
    }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return null

    return resolver.openOutputStream(downloadUri).use { bitmapStream ->
        if (bitmapStream != null) {
            bitmap.compressPng(bitmapStream)
            bitmap.recycle()
            downloadUri
        } else {
            null
        }
    }
}

internal fun Context.saveBitmapToDownloadsDirLegacy(bitmap: Bitmap, fileName: String): Uri? {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath + "/$QRCODE_DIRECTORY_NAME_FULL"
        )
        return try {
            if (!downloadsDirectory.exists()) {
                downloadsDirectory.mkdirs()
            }
            val file = File(downloadsDirectory, fileName)
            FileOutputStream(file).use {
                bitmap.compressPng(it)
            }
            bitmap.recycle()
            provideFileUri(file)
        } catch (e: Exception) {
            null
        }
    }
    return null
}

fun Context.provideFileUri(file: File): Uri = FileProvider.getUriForFile(
    this,
    "$packageName.provider",
    file
)


fun Uri.fileContent(context: Context): String? {
    val item = context.contentResolver.openInputStream(this)
    val bytes = item?.readBytes()
    return bytes?.toString(Charsets.UTF_8)
}


fun Uri.fileName(context: Context): String {
    val cursor = context.contentResolver.query(this, null, null, null, null)
    return cursor?.use {
        val nameColumnIndex = it.getColumnIndex(DISPLAY_NAME)
        it.moveToFirst()
        it.getString(nameColumnIndex)
    } ?: ""
}

internal fun Bitmap.compressPng(stream: OutputStream) =
    compress(Bitmap.CompressFormat.PNG, 100, stream)


suspend fun Uri.processZip(context: Context): List<AppZipEntry> = withContext(Dispatchers.IO) {
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
                            val fileContent = zipInputStream.readBytes().toString(Charsets.UTF_8)
                            coroutineContext.ensureActive()
                            entries.add(AppZipEntry(entryName, fileContent))
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing file: $entryName")
                        }
                    }
                }

                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }
        }
    }
    return@withContext entries
}
