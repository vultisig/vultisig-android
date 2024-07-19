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
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException


@RequiresApi(Build.VERSION_CODES.Q)
internal fun Context.backupVaultToDownloadsDirAtLeastQ(json: String, backupFileName: String): Boolean {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
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
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val jsonFile = File(downloadsDirectory, backupFileName)
        try {
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
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }

    val resolver = contentResolver

    val downloadUri: Uri =
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        resolver.openOutputStream(downloadUri).use { bitmapStream ->
            if (bitmapStream != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream)
                downloadUri
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

internal fun saveBitmapToDownloadsDir(bitmap: Bitmap, fileName: String): Uri? {
    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDirectory, fileName)
        return try {
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            Uri.fromFile(file)
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
