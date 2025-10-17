package com.vultisig.wallet.data.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import javax.inject.Inject
import kotlin.math.roundToInt

private const val SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE = 0.1f
private const val SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE = 0.06f
private const val SHARE_QR_CODE_LOGO_SCALE_SCALE = 0.4f
private const val VULTISIG_ADDRESS = "vultisig.com"

interface MakeQrCodeBitmapShareFormat : (Context, Bitmap, Int, Bitmap, String, String?) -> Bitmap

private const val QR_CODE_MIN_SIZE_DP = 200
private const val QR_CODE_MAX_SIZE_DP = 400

fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).roundToInt()
}

internal class MakeQrCodeBitmapShareFormatImpl @Inject constructor(
) : MakeQrCodeBitmapShareFormat {
    override fun invoke(
        context: Context,
        qrCodeBitmap: Bitmap,
        color: Int,
        logo: Bitmap,
        title: String,
        description: String?,
    ): Bitmap {
        // Ensure QR code rendering is independent of screen pixel density.
        // By using DP for sizing, the QR code maintains a consistent physical
        // size across devices with varying densities.
        // This prevents issues where the QR code appears too small on high-density
        // screens or too large on low-density screens, which could otherwise lead
        // to scanning difficulties with ML or QR code libraries.

        val minQrCodePx = QR_CODE_MIN_SIZE_DP.dpToPx(context)
        val maxQrCodePx = QR_CODE_MAX_SIZE_DP.dpToPx(context)

        val qrBitmap = when {
            qrCodeBitmap.width < minQrCodePx -> {
                qrCodeBitmap.scale(minQrCodePx, minQrCodePx, false)
            }
            qrCodeBitmap.width > maxQrCodePx -> {
                qrCodeBitmap.scale(maxQrCodePx, maxQrCodePx, false)
            }
            else -> {
                qrCodeBitmap
            }
        }

        val width = qrBitmap.width
        val height = qrBitmap.height
        val config = qrBitmap.config
        val padding = (width * SHARE_QR_CODE_REGULAR_PADDING_WIDTH_SCALE).toInt()
        val textSize = width * SHARE_QR_CODE_REGULAR_TEXT_SIZE_WIDTH_SCALE
        val logoWidth = (width * SHARE_QR_CODE_LOGO_SCALE_SCALE).toInt()
        val descLines = description?.split("\n")
        val scaledLogo = logo.scale(logoWidth, logoWidth)
        val textColor = Color.WHITE

        var finalHeight = height + 2 * padding
        finalHeight += (textSize * 2).toInt()
        if (!description.isNullOrBlank()) {
            val descNumberLines = descLines?.size ?: 0
            finalHeight += (textSize * 3 / 2 * descNumberLines).toInt()
        }
        finalHeight += logoWidth + padding
        finalHeight += (textSize * 2).toInt()

        val finalWidth = width + 2 * padding
        val bitmap = createBitmap(finalWidth, finalHeight, config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        canvas.drawBitmap(qrBitmap, padding.toFloat(), padding.toFloat(), null)

        val paint = Paint().apply {
            this.textSize = textSize
            this.color = textColor
            this.textAlign = Paint.Align.CENTER
            this.isAntiAlias = true
        }

        canvas.drawText(
            title,
            finalWidth / 2f,
            padding + height + textSize * 3 / 2,
            paint
        )

        descLines?.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                finalWidth / 2f,
                padding + height + textSize * 3 + textSize / 2 + index * textSize * 3 / 2,
                paint
            )
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

        return bitmap
    }
}