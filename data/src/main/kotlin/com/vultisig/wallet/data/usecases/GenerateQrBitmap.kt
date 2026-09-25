package com.vultisig.wallet.data.usecases

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject

private const val QR_CODE_SCALE_FACTOR = 8
private const val QR_CODE_VS_LOGO_SCALE_FACTOR = 4

// Quiet-zone margin, in modules, baked into the matrix so the finder patterns are inset from the
// edges instead of running flush to the corners. Required for reliable scanning — especially when
// the code is shown edge-to-edge full-screen, and so corner overlays (e.g. the expand control) keep
// clear of the finder patterns. The QR spec recommends 4 modules.
private const val QR_CODE_QUIET_ZONE = 4

interface GenerateQrBitmap : (String, Int, Int, Bitmap?) -> Bitmap

class GenerateQrBitmapImpl @Inject constructor() : GenerateQrBitmap {
    override fun invoke(
        qrCodeContent: String,
        @ColorInt mainColor: Int,
        @ColorInt backgroundColor: Int,
        logo: Bitmap?,
    ): Bitmap {
        // Logos overwrite modules in the center, so bump error correction well past the ZXing
        // default (L, ~7%) to leave enough recovery budget for the overwritten area — otherwise
        // stricter third-party decoders (e.g. iOS's AVFoundation/Vision scanner) fail to decode.
        val hintMap =
            mapOf(EncodeHintType.MARGIN to QR_CODE_QUIET_ZONE) +
                if (logo != null) {
                    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
                } else {
                    emptyMap()
                }

        val qrCodeWriter = QRCodeWriter()
        val bitmapMatrix = qrCodeWriter.encode(qrCodeContent, BarcodeFormat.QR_CODE, 0, 0, hintMap)

        // Expand each module to a QR_CODE_SCALE_FACTOR square straight into one pixel buffer, so
        // the full-resolution bitmap is written in a single call instead of pixel by pixel and
        // then upscaled into a second copy.
        val matrixWidth = bitmapMatrix.width
        val matrixHeight = bitmapMatrix.height
        val scaledWidth = matrixWidth * QR_CODE_SCALE_FACTOR
        val scaledHeight = matrixHeight * QR_CODE_SCALE_FACTOR

        val pixels = IntArray(scaledWidth * scaledHeight)
        val row = IntArray(scaledWidth)
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                val pixelColor = if (bitmapMatrix.get(x, y)) mainColor else backgroundColor
                row.fill(pixelColor, x * QR_CODE_SCALE_FACTOR, (x + 1) * QR_CODE_SCALE_FACTOR)
            }
            for (dy in 0 until QR_CODE_SCALE_FACTOR) {
                row.copyInto(pixels, (y * QR_CODE_SCALE_FACTOR + dy) * scaledWidth)
            }
        }

        val scaledBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        scaledBitmap.setPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)

        if (logo == null) {
            return scaledBitmap
        }

        val scaledLogoWidthTemp = scaledWidth / QR_CODE_VS_LOGO_SCALE_FACTOR
        val scaledLogoHeightTemp = scaledHeight / QR_CODE_VS_LOGO_SCALE_FACTOR
        val scaledLogoWidth = if (scaledLogoWidthTemp == 0) 1 else scaledLogoWidthTemp
        val scaledLogoHeight = if (scaledLogoHeightTemp == 0) 1 else scaledLogoHeightTemp
        // `logo` comes from `decodeResource` on the call sites; some devices hand back a bitmap
        // with a null color space, which raw `scale` rejects. Use the color-space-safe scale so a
        // shared/exported QR can never crash on this input.
        val scaledLogo = logo.scaleWithColorSpace(scaledLogoWidth, scaledLogoHeight)
        val canvas = android.graphics.Canvas(scaledBitmap)

        val xLogo = (scaledBitmap.width - scaledLogo.width) / 2f
        val yLogo = (scaledBitmap.height - scaledLogo.height) / 2f

        canvas.drawBitmap(scaledLogo, xLogo, yLogo, null)
        if (scaledLogo != logo) {
            scaledLogo.recycle()
        }

        return scaledBitmap
    }
}
