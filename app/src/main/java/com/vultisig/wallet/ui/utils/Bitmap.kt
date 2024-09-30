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
private const val SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE = 0.1f
private const val SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE = 0.06f
private const val SHARE_QR_CODE_LOGO_SCALE_SCALE = 0.4f
private const val VULTISIG_ADDRESS = "vultisig.com"

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

internal fun Bitmap.makeShareFormat(
    color: Int,
    logo : Bitmap,
    title: String,
    description: String? = null,
    textColor: Int = Color.WHITE,
): Bitmap {
    val padding = (width * SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE).toInt()
    val textSize = width * SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE
    val logoWidth = (width * SHARE_QR_CODE_LOGO_SCALE_SCALE).toInt()
    val descLines = description?.split("\n")
    val scaledLogo = logo.scale(logoWidth, logoWidth)
    logo.recycle()
    
    var finalHeight = height + 2 * padding
    finalHeight += (textSize * 2).toInt()
    val descNumberLines = descLines?.size ?: 0
    finalHeight += (textSize * 3 / 2 * descNumberLines).toInt()
    finalHeight += logoWidth + padding
    finalHeight += (textSize * 2).toInt()
    
    val finalWidth = width + 2 * padding
    val bitmap = Bitmap.createBitmap(
        finalWidth,
        finalHeight,
        config
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(color)
    canvas.drawBitmap(this, padding.toFloat(), padding.toFloat(), null)

    val paint = android.graphics.Paint()
    paint.textSize = textSize
    paint.color = textColor
    paint.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText(
        title,
        finalWidth / 2f,
        padding + height + textSize * 3 / 2,
        paint
    )

    if (description != null) {
        descLines?.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                finalWidth / 2f,
                padding + height + textSize * 3 + textSize / 2 + index * textSize * 3 / 2,
                paint
            )
        }
    }

    canvas.drawBitmap(
        scaledLogo,
        (finalWidth - logoWidth) / 2f,
        finalHeight - padding - 2 * textSize - logoWidth,
        null
    )

    canvas.drawText(
        VULTISIG_ADDRESS,
        finalWidth / 2f,
        finalHeight - padding - textSize / 2,
        paint
    )

    this.recycle()
    return bitmap
}
