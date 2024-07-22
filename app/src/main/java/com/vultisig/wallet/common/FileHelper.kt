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

const val DIRECTORY_NAME = "Vultisig"
const val QRCODE_DIRECTORY_NAME = "QRCodes"
const val BACKUPS_DIRECTORY_NAME = "Backups"


@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.backupVaultToDownloadsDirAtLeastQ(json: String, backupFileName: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$DIRECTORY_NAME/$BACKUPS_DIRECTORY_NAME"
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

internal fun backupVaultToDownloadsDir(json: String, backupFileName: String): Boolean {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath + "/$DIRECTORY_NAME/$BACKUPS_DIRECTORY_NAME"
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

@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.saveBitmapToDownloadsDirAtLeastQ(bitmap: Bitmap, fileName: String): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(
            MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS + "/$DIRECTORY_NAME/$QRCODE_DIRECTORY_NAME"
        )
    }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        resolver.openOutputStream(downloadUri).use { bitmapStream ->
            if (bitmapStream != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream)
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

internal fun Context.saveBitmapToDownloadsDir(bitmap: Bitmap, fileName: String): Uri? {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath + "/$DIRECTORY_NAME/$QRCODE_DIRECTORY_NAME"
        )
        return try {
            if (!downloadsDirectory.exists()) {
                downloadsDirectory.mkdirs()
            }
            val file = File(downloadsDirectory, fileName)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
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
