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

private const val QR_CODE_SCALE_FACTOR = 8
private const val QR_CODE_VS_LOGO_SCALE_FACTOR = 4

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
    this.recycle()
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

internal fun generateQrBitmap(
    qrCodeContent: String,
    mainColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black,
    backgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    logo : Bitmap? = null,
): Bitmap {
    val hintMap = mapOf(EncodeHintType.MARGIN to 0)

    val qrCodeWriter = QRCodeWriter()
    val bitmapMatrix = qrCodeWriter.encode(
        qrCodeContent,
        BarcodeFormat.QR_CODE,
        0,
        0,
        hintMap
    )

    val matrixWidth = bitmapMatrix.width
    val matrixHeight = bitmapMatrix.height

    val bitmap = Bitmap.createBitmap(
        matrixWidth,
        matrixHeight,
        Bitmap.Config.ARGB_8888,
    )

    for (x in 0 until matrixWidth) {
        for (y in 0 until matrixHeight) {
            val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
            val pixelColor =
                if (shouldColorPixel) mainColor.toArgb() else backgroundColor.toArgb()

            bitmap.setPixel(x, y, pixelColor)
        }
    }
    if (logo == null) return bitmap

    val scaledWidth = bitmap.width * QR_CODE_SCALE_FACTOR
    val scaledHeight = bitmap.height * QR_CODE_SCALE_FACTOR
    val scaledLogoWidthTemp = scaledWidth / QR_CODE_VS_LOGO_SCALE_FACTOR
    val scaledLogoHeightTemp = scaledHeight / QR_CODE_VS_LOGO_SCALE_FACTOR
    val scaledLogoWidth = if (scaledLogoWidthTemp == 0) 1 else scaledLogoWidthTemp
    val scaledLogoHeight = if (scaledLogoHeightTemp == 0) 1 else scaledLogoHeightTemp
    val scaledLogo = logo.scale(scaledLogoWidth, scaledLogoHeight)
    val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight, false)

    val canvas = android.graphics.Canvas(scaledBitmap)

    val xLogo = (scaledBitmap.width - scaledLogo.width) / 2f
    val yLogo = (scaledBitmap.height - scaledLogo.height) / 2f

    canvas.drawBitmap(scaledLogo, xLogo, yLogo, null)
    if (bitmap != scaledBitmap)
        bitmap.recycle()

    scaledLogo.recycle()

    return scaledBitmap
}