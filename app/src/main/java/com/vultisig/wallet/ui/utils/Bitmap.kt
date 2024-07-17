package com.vultisig.wallet.ui.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.FileDescriptor
import java.io.IOException

internal fun uriToBitmap(contentResolver: ContentResolver, selectedFileUri: Uri): Bitmap? {
    try {
        val parcelFileDescriptor = contentResolver.openFileDescriptor(selectedFileUri, "r")
        val fileDescriptor: FileDescriptor = requireNotNull(parcelFileDescriptor).fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()
        return image
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return null
}

internal fun Bitmap.addWhiteBorder(borderSize: Float): Bitmap {
    val bmpWithBorder =
        Bitmap.createBitmap(width + (borderSize * 2).toInt(), height + (borderSize * 2).toInt(), config)
    val canvas = Canvas(bmpWithBorder)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(this, borderSize, borderSize, null)
    return bmpWithBorder
}