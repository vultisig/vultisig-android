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
        contentResolver.openFileDescriptor(selectedFileUri, "r").use {
            val fileDescriptor: FileDescriptor = requireNotNull(it).fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            return image
        }
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
    this.recycle()
    return bmpWithBorder
}