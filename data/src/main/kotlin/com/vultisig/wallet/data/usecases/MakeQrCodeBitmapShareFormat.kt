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

private const val SHARE_QR_CODE_PADDING_DP = 16
private const val SHARE_QR_CODE_TITLE_TEXT_SIZE_DP = 16
private const val SHARE_QR_CODE_DESCRIPTION_TEXT_SIZE_DP = 14
private const val SHARE_QR_CODE_LOGO_SIZE_DP = 64
private const val SHARE_QR_CODE_FOOTER_TEXT_SIZE_DP = 12 // vultisig.com text size

const val GAP_QR_TO_TITLE_DP = 6
const val GAP_TITLE_TO_DESCRIPTION_DP = 24
const val GAP_DESCRIPTION_TO_LOGO_DP = 24
const val GAP_LOGO_TO_FOOTER_DP = 12

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
        if (description != null) {
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


/*fun invoke(
    qrCodeBitmap: Bitmap,
    color: Int,
    logo: Bitmap,
    title: String,
    description: String?,
): Bitmap {
    val qrBitmap = if (qrCodeBitmap.width < 600)
        qrCodeBitmap.scale(600, 600, false)
    else qrCodeBitmap

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
    if (description != null) {
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

    val paint = Paint()
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

    return bitmap
}
 */

/*

override fun invoke(
        context: Context,
        qrCodeBitmap: Bitmap,
        color: Int,
        logo: Bitmap,
        title: String,
        description: String?,
    ): Bitmap {
        val minQrCodePx = QR_CODE_MIN_SIZE_DP.dpToPx(context)
        val qrBitmap = if (qrCodeBitmap.width < minQrCodePx) {
            qrCodeBitmap.scale(minQrCodePx, minQrCodePx, false)
        } else {
            // If the QR code is too large scale down (high screens, sometimes it does not filled)
            val maxQrCodePx = 400.dpToPx(context)
            if (qrCodeBitmap.width > maxQrCodePx) {
                qrCodeBitmap.scale(maxQrCodePx, maxQrCodePx, false)
            } else {
                qrCodeBitmap
            }
        }

        val qrWidthPx = qrBitmap.width
        val qrHeightPx = qrBitmap.height
        val config = qrBitmap.config

        // Calculate all sizes in DP, to make it independent of screens
        val paddingPx = SHARE_QR_CODE_PADDING_DP.dpToPx(context)
        val titleTextSizePx = SHARE_QR_CODE_TITLE_TEXT_SIZE_DP.dpToPx(context)
        val descriptionTextSizePx = SHARE_QR_CODE_DESCRIPTION_TEXT_SIZE_DP.dpToPx(context)
        val logoSizePx = SHARE_QR_CODE_LOGO_SIZE_DP.dpToPx(context)
        val footerTextSizePx = SHARE_QR_CODE_FOOTER_TEXT_SIZE_DP.dpToPx(context)

        val gapQrToTitlePx = GAP_QR_TO_TITLE_DP.dpToPx(context)
        val gapTitleToDescriptionPx = GAP_TITLE_TO_DESCRIPTION_DP.dpToPx(context)
        val gapDescriptionToLogoPx = GAP_DESCRIPTION_TO_LOGO_DP.dpToPx(context)
        val gapLogoToFooterPx = GAP_LOGO_TO_FOOTER_DP.dpToPx(context)

        // Pre-calculate estimated line heights for text to help with layout
        val titleLineHeightPx = (titleTextSizePx * 1.4).roundToInt()
        val descLineHeightPx = (descriptionTextSizePx * 1.2).roundToInt()
        val footerLineHeightPx = (footerTextSizePx * 1.2).roundToInt()

        // ----- Calculate Size -----
        val descLines = description?.split("\n")

        // Start with QR code size
        var calculatedHeight = paddingPx.toFloat() // Top padding
        calculatedHeight += qrHeightPx // QR Code height

        // Space between QR and Title
        calculatedHeight += gapQrToTitlePx

        // Title height
        calculatedHeight += titleLineHeightPx

        // Description area
        if (description != null) {
            calculatedHeight += gapTitleToDescriptionPx
            val descNumberLines = descLines?.size ?: 0
            calculatedHeight += descNumberLines * descLineHeightPx
        }

        // Logo area
        calculatedHeight += gapDescriptionToLogoPx
        calculatedHeight += logoSizePx

        // Footer text area (vultisig.com)
        calculatedHeight += gapLogoToFooterPx
        calculatedHeight += footerLineHeightPx

        calculatedHeight += paddingPx // Bottom padding

        val finalHeightPx = calculatedHeight.roundToInt()
        val finalWidthPx = (qrWidthPx + 2 * paddingPx) // Width based on QR + padding

        val bitmap = createBitmap(finalWidthPx, finalHeightPx, config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)

        // ----- Drawing ----

        // Draw QR Code
        canvas.drawBitmap(qrBitmap, paddingPx.toFloat(), paddingPx.toFloat(), null)
        var drawY = paddingPx.toFloat() + qrHeightPx

        // Draw Title
        val titlePaint = Paint().apply {
            textSize = titleTextSizePx.toFloat()
            textAlign = Paint.Align.CENTER
            setColor(Color.WHITE)
        }

        drawY += gapQrToTitlePx // Move drawY past the gap
        canvas.drawText(title, finalWidthPx / 2f, drawY + titleLineHeightPx, titlePaint) // Draw title with its height considered
        drawY += titleLineHeightPx // Update drawY for the next element

        // Draw Description
        if (description != null) {
            val descPaint = Paint().apply {
                textSize = descriptionTextSizePx.toFloat()
                textAlign = Paint.Align.CENTER
                setColor(Color.WHITE)
            }
            drawY += paddingPx / 2
            descLines?.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    finalWidthPx / 2f,
                    drawY + index * descLineHeightPx,
                    descPaint
                )
            }
            drawY += descLines?.size?.times(descLineHeightPx) ?: 0
        }

        // Draw Logo
        val scaledLogo = logo.scale(logoSizePx, logoSizePx)
        drawY += paddingPx
        canvas.drawBitmap(
            scaledLogo,
            (finalWidthPx - logoSizePx) / 2f,
            drawY,
            null
        )
        drawY += logoSizePx

        // Draw Footer
        val footerPaint = Paint().apply {
            textSize = footerTextSizePx.toFloat()
            textAlign = Paint.Align.CENTER
            setColor(Color.WHITE)
        }

        drawY += paddingPx
        canvas.drawText(
            VULTISIG_ADDRESS,
            finalWidthPx / 2f,
            drawY,
            footerPaint
        )

        return bitmap
    }
 */