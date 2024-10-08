package com.vultisig.wallet.data.usecases

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import javax.inject.Inject

private const val SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE = 0.1f
private const val SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE = 0.06f
private const val SHARE_QR_CODE_LOGO_SCALE_SCALE = 0.4f
private const val VULTISIG_ADDRESS = "vultisig.com"

interface MakeQrCodeBitmapShareFormat : (Bitmap, Int, Bitmap, String, String?) -> Bitmap

internal class MakeQrCodeBitmapShareFormatImpl @Inject constructor() : MakeQrCodeBitmapShareFormat {
    override fun invoke(
        qrCodeBitmap: Bitmap,
        color: Int,
        logo: Bitmap,
        title: String,
        description: String?,
    ): Bitmap {
        val width = qrCodeBitmap.width
        val height = qrCodeBitmap.height
        val config = qrCodeBitmap.config
        val padding = (width * SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE).toInt()
        val textSize = width * SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE
        val logoWidth = (width * SHARE_QR_CODE_LOGO_SCALE_SCALE).toInt()
        val descLines = description?.split("\n")
        val scaledLogo = logo.scale(logoWidth, logoWidth)
        val textColor = Color.WHITE
        if (scaledLogo != logo) {
            logo.recycle()
        }

        var finalHeight = height + 2 * padding
        finalHeight += (textSize * 2).toInt()
        if (description != null) {
            val descNumberLines = descLines?.size ?: 0
            finalHeight += (textSize * 3 / 2 * descNumberLines).toInt()
        }
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
        canvas.drawBitmap(qrCodeBitmap, padding.toFloat(), padding.toFloat(), null)

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

        qrCodeBitmap.recycle()
        return bitmap
    }
}