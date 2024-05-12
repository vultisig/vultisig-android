package com.vultisig.wallet.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.vultisig.wallet.data.on_board.db.VaultDB
import java.net.URL

@RequiresApi(Build.VERSION_CODES.Q)
fun Context.downloadVault(vaultName: String):Boolean {

    val targetFile = filesDir.resolve("vaults").resolve(vaultName+VaultDB.FILE_POSTFIX)

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }

    val resolver = contentResolver

    val downloadUri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false

    URL("file://" + targetFile.absolutePath).openStream().use { input ->
        try {
            resolver.openOutputStream(downloadUri).use { output ->
                input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }
}

