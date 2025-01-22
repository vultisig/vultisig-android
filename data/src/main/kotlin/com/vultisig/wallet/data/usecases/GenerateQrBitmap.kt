package com.vultisig.wallet.data.usecases

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject

private const val QR_CODE_SCALE_FACTOR = 8
private const val QR_CODE_VS_LOGO_SCALE_FACTOR = 4

interface GenerateQrBitmap : (String, Color, Color, Bitmap?) -> Bitmap

internal class GenerateQrBitmapImpl @Inject constructor() : GenerateQrBitmap {
    override fun invoke(
        qrCodeContent: String,
        mainColor: Color,
        backgroundColor: Color,
        logo: Bitmap?,
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

        if (bitmapMatrix == null) {
            return bitmap
        }

        for (x in 0 until matrixWidth) {
            for (y in 0 until matrixHeight) {
                val shouldColorPixel = bitmapMatrix.get(x, y)
                val pixelColor =
                    if (shouldColorPixel) mainColor.toArgb() else backgroundColor.toArgb()

                bitmap.setPixel(x, y, pixelColor)
            }
        }

        if (logo == null) {
            return bitmap
        }

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
        if (bitmap != scaledBitmap) {
            bitmap.recycle()
        }

        if (scaledLogo != logo) {
            scaledLogo.recycle()
        }

        return scaledBitmap
    }
}