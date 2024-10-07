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
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
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
    val canvas = android.graphics.Canvas(bmpWithBorder)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(this, borderSize, borderSize, null)
    if (!this.isRecycled) {
        this.recycle()
    }

    return bmpWithBorder
}

internal fun Modifier.extractBitmap(processBitmap: (bitmap: Bitmap) -> Unit): Modifier =
    drawWithCache {
        val width = this.size.width.toInt()
        val height = this.size.height.toInt()
        onDrawWithContent {
            val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pictureCanvas = Canvas(android.graphics.Canvas(tempBitmap))
            draw(this, this.layoutDirection, pictureCanvas, this.size) {
                this@onDrawWithContent.drawContent()
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(
                    tempBitmap,
                    0f,
                    0f,
                    null
                )
            }
            processBitmap(tempBitmap)
        }
    }