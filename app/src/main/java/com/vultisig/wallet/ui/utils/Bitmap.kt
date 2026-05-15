package com.vultisig.wallet.ui.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.createBitmap
import java.io.FileDescriptor
import java.io.IOException

internal fun uriToBitmap(
    contentResolver: ContentResolver,
    selectedFileUri: Uri,
    maxDimension: Int = 1600,
): Bitmap? {
    require(maxDimension > 0) { "maxDimension must be > 0" }
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openFileDescriptor(selectedFileUri, "r").use {
            val fileDescriptor: FileDescriptor = requireNotNull(it).fileDescriptor
            BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bounds)
        }
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        var sampleSize = 1
        while (largest / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        contentResolver.openFileDescriptor(selectedFileUri, "r").use {
            val fileDescriptor: FileDescriptor = requireNotNull(it).fileDescriptor
            return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, decodeOptions)
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return null
}

internal fun Bitmap.addWhiteBorder(borderSize: Float): Bitmap {
    val bmpWithBorder =
        createBitmap(
            width + (borderSize * 2).toInt(),
            height + (borderSize * 2).toInt(),
            config ?: Bitmap.Config.ARGB_8888,
        )
    val canvas = android.graphics.Canvas(bmpWithBorder)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(this, borderSize, borderSize, null)
    return bmpWithBorder
}

internal fun Modifier.extractBitmap(processBitmap: (bitmap: Bitmap) -> Unit): Modifier =
    drawWithCache {
        val width = this.size.width.toInt()
        val height = this.size.height.toInt()
        onDrawWithContent {
            val tempBitmap = createBitmap(width, height)
            val pictureCanvas = Canvas(android.graphics.Canvas(tempBitmap))
            draw(this, this.layoutDirection, pictureCanvas, this.size) {
                this@onDrawWithContent.drawContent()
            }
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawBitmap(tempBitmap, 0f, 0f, null) }
            processBitmap(tempBitmap)
        }
    }
