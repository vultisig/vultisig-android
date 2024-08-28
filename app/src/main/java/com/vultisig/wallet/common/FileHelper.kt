package com.vultisig.wallet.common

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
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream

private const val DIRECTORY_NAME = "Vultisig"
private const val QRCODE_DIRECTORY_NAME = "QRCodes"
private const val BACKUPS_DIRECTORY_NAME = "Backups"
internal const val QRCODE_DIRECTORY_NAME_FULL = "$DIRECTORY_NAME/$QRCODE_DIRECTORY_NAME"
internal const val BACKUPS_DIRECTORY_NAME_FULL = "$DIRECTORY_NAME/$BACKUPS_DIRECTORY_NAME"


@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.backupVaultToDownloadsDirAtLeastQ(json: String, backupFileName: String): Boolean {
    val resolver = contentResolver
    var uniqueFileName = backupFileName
    var fileIndex = 1

    // Check for existing file and generate a unique file name
    while (true) {
        val existingFileUri = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(uniqueFileName),
            null
        )
        if (existingFileUri?.moveToFirst() == true) {
            val fileExtension = backupFileName.substringAfterLast('.', "")
            val fileNameWithoutExtension = backupFileName.substringBeforeLast('.', backupFileName)
            uniqueFileName = "$fileNameWithoutExtension($fileIndex).$fileExtension"
            fileIndex++
        } else {
            break
        }
        existingFileUri?.close()
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueFileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$BACKUPS_DIRECTORY_NAME_FULL"
        )
    }


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

internal fun Context.saveBitmapToDownloads(bitmap: Bitmap, fileName: String): Uri?{
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveBitmapToDownloadsDirAtLeastQ(bitmap, fileName)
    } else {
        saveBitmapToDownloadsDirLegacy(bitmap, fileName)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.saveBitmapToDownloadsDirAtLeastQ(bitmap: Bitmap, fileName: String): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$QRCODE_DIRECTORY_NAME_FULL"
        )
    }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        resolver.openOutputStream(downloadUri).use { bitmapStream ->
            if (bitmapStream != null) {
                bitmap.compressPng(bitmapStream)
                bitmap.recycle()
                downloadUri
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
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
            FileProvider.getUriForFile(
                this, "$packageName.provider", file
            )
        } catch (e: Exception) {
            null
        }
    }
    return null
}


internal fun Uri.fileContent(context: Context): String? {
    val item = context.contentResolver.openInputStream(this)
    val bytes = item?.readBytes()
    return bytes?.toString(Charsets.UTF_8)
}


internal fun Uri.fileName(context: Context): String {
    val cursor = context.contentResolver.query(this, null, null, null, null)
    cursor.use {
        val nameColumnIndex = it!!.getColumnIndex(DISPLAY_NAME)
        it.moveToFirst()
        val fileName = it.getString(nameColumnIndex)
        return fileName ?: ""
    }
}

internal fun Bitmap.compressPng(stream: OutputStream) =
    compress(Bitmap.CompressFormat.PNG, 100, stream)
