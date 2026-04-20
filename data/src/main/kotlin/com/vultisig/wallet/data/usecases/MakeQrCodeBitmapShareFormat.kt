package com.vultisig.wallet.data.usecases

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import javax.inject.Inject
import kotlin.math.roundToInt

private const val QR_CODE_MIN_SIZE_DP = 200
private const val QR_CODE_MAX_SIZE_DP = 400

private const val OUTER_PADDING_DP = 30f
private const val CARD_CORNER_RADIUS_DP = 24f
private const val CARD_BORDER_WIDTH_DP = 1f
private const val QR_CARD_INNER_PADDING_DP = 4f
private const val BETWEEN_CARDS_GAP_DP = 18f

private const val META_CARD_PADDING_DP = 20f
private const val META_TITLE_TO_FIELDS_GAP_DP = 28f
private const val META_FIELD_ROW_GAP_DP = 14f
private const val META_DIVIDER_HEIGHT_DP = 1f
private const val META_TITLE_LINE_HEIGHT_DP = 20f
private const val META_FIELD_LINE_HEIGHT_DP = 16f
private const val META_TITLE_FONT_SIZE_DP = 14f
private const val META_FIELD_FONT_SIZE_DP = 12f

private const val SIGNATURE_TOP_GAP_DP = 22f
private const val SIGNATURE_ICON_SIZE_DP = 33f
private const val SIGNATURE_ICON_TO_TEXT_GAP_DP = 10f
private const val SIGNATURE_TEXT_SIZE_DP = 12f

private const val VALUE_ICON_SIZE_DP = 16f
private const val VALUE_ICON_TO_TEXT_GAP_DP = 4f

private const val CARD_BACKGROUND_COLOR = 0xFF061B3A.toInt()
private const val BORDER_COLOR = 0xFF11284A.toInt()
private const val TEXT_PRIMARY_COLOR = 0xFFF0F4FC.toInt()
private const val TEXT_TERTIARY_COLOR = 0xFF8295AE.toInt()

data class QrShareField(val label: String, val value: String, val valueIcon: Bitmap? = null)

data class QrShareInfo(val title: String, val fields: List<QrShareField>)

interface MakeQrCodeBitmapShareFormat : (Context, Bitmap, Int, Bitmap, QrShareInfo) -> Bitmap

private fun Float.dp(context: Context): Float = this * context.resources.displayMetrics.density

// Resolve the app's Brockmann Medium font from this shared data-module use case. The font lives
// in the app module's resources, so it is not reachable via R.font here — name lookup is the only
// option available across modules.
@SuppressLint("DiscouragedApi")
private fun loadBrockmannMedium(context: Context): Typeface {
    val id = context.resources.getIdentifier("brockmann_medium", "font", context.packageName)
    return id.takeIf { it != 0 }?.let { ResourcesCompat.getFont(context, it) }
        ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

internal class MakeQrCodeBitmapShareFormatImpl @Inject constructor() : MakeQrCodeBitmapShareFormat {
    override fun invoke(
        context: Context,
        qrCodeBitmap: Bitmap,
        color: Int,
        logo: Bitmap,
        info: QrShareInfo,
    ): Bitmap {
        val density = context.resources.displayMetrics.density

        val minQrPx = (QR_CODE_MIN_SIZE_DP * density).roundToInt()
        val maxQrPx = (QR_CODE_MAX_SIZE_DP * density).roundToInt()
        val qrBitmap =
            when {
                qrCodeBitmap.width < minQrPx -> qrCodeBitmap.scale(minQrPx, minQrPx, false)
                qrCodeBitmap.width > maxQrPx -> qrCodeBitmap.scale(maxQrPx, maxQrPx, false)
                else -> qrCodeBitmap
            }

        val outerPadding = OUTER_PADDING_DP.dp(context)
        val cardCorner = CARD_CORNER_RADIUS_DP.dp(context)
        val borderWidth = CARD_BORDER_WIDTH_DP.dp(context)
        val qrCardInnerPadding = QR_CARD_INNER_PADDING_DP.dp(context)
        val betweenCardsGap = BETWEEN_CARDS_GAP_DP.dp(context)
        val metaPadding = META_CARD_PADDING_DP.dp(context)
        val titleToFieldsGap = META_TITLE_TO_FIELDS_GAP_DP.dp(context)
        val fieldRowGap = META_FIELD_ROW_GAP_DP.dp(context)
        val dividerHeight = META_DIVIDER_HEIGHT_DP.dp(context)
        val titleLineHeight = META_TITLE_LINE_HEIGHT_DP.dp(context)
        val fieldLineHeight = META_FIELD_LINE_HEIGHT_DP.dp(context)
        val titleFontSize = META_TITLE_FONT_SIZE_DP.dp(context)
        val fieldFontSize = META_FIELD_FONT_SIZE_DP.dp(context)
        val signatureTopGap = SIGNATURE_TOP_GAP_DP.dp(context)
        val signatureIconSize = SIGNATURE_ICON_SIZE_DP.dp(context)
        val signatureIconToTextGap = SIGNATURE_ICON_TO_TEXT_GAP_DP.dp(context)
        val signatureTextSize = SIGNATURE_TEXT_SIZE_DP.dp(context)

        val cardOuterWidth = qrBitmap.width.toFloat() + 2 * qrCardInnerPadding

        val rowsCount = info.fields.size
        val dividersCount = (rowsCount - 1).coerceAtLeast(0)
        val metaRowsBlockHeight =
            rowsCount * fieldLineHeight +
                dividersCount * dividerHeight +
                (rowsCount + dividersCount - 1).coerceAtLeast(0) * fieldRowGap
        val metaCardHeight =
            metaPadding * 2 + titleLineHeight + titleToFieldsGap + metaRowsBlockHeight

        val signatureBlockHeight = signatureIconSize + signatureIconToTextGap + signatureTextSize

        val finalWidth = (cardOuterWidth + outerPadding * 2).roundToInt()
        val finalHeight =
            (outerPadding +
                    cardOuterWidth +
                    betweenCardsGap +
                    metaCardHeight +
                    signatureTopGap +
                    signatureBlockHeight +
                    outerPadding)
                .roundToInt()

        val bitmap =
            createBitmap(finalWidth, finalHeight, qrBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)

        val borderPaint =
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                this.color = BORDER_COLOR
            }
        val cardFillPaint =
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                this.color = CARD_BACKGROUND_COLOR
            }

        val cardLeft = outerPadding
        val cardRight = cardLeft + cardOuterWidth

        val qrCardTop = outerPadding
        val qrCardBottom = qrCardTop + cardOuterWidth
        val qrCardRect = RectF(cardLeft, qrCardTop, cardRight, qrCardBottom)
        canvas.drawRoundRect(qrCardRect, cardCorner, cardCorner, borderPaint)
        canvas.drawBitmap(
            qrBitmap,
            cardLeft + qrCardInnerPadding,
            qrCardTop + qrCardInnerPadding,
            null,
        )

        val metaCardTop = qrCardBottom + betweenCardsGap
        val metaCardBottom = metaCardTop + metaCardHeight
        val metaCardRect = RectF(cardLeft, metaCardTop, cardRight, metaCardBottom)
        canvas.drawRoundRect(metaCardRect, cardCorner, cardCorner, cardFillPaint)
        canvas.drawRoundRect(metaCardRect, cardCorner, cardCorner, borderPaint)

        val titleTypeface = loadBrockmannMedium(context)
        val titlePaint =
            Paint().apply {
                isAntiAlias = true
                this.color = TEXT_PRIMARY_COLOR
                textSize = titleFontSize
                typeface = titleTypeface
                textAlign = Paint.Align.LEFT
            }

        val labelPaint =
            Paint().apply {
                isAntiAlias = true
                this.color = TEXT_TERTIARY_COLOR
                textSize = fieldFontSize
                typeface = titleTypeface
                textAlign = Paint.Align.LEFT
            }
        val valuePaint =
            Paint().apply {
                isAntiAlias = true
                this.color = TEXT_PRIMARY_COLOR
                textSize = fieldFontSize
                typeface = titleTypeface
                textAlign = Paint.Align.RIGHT
            }
        val dividerPaint =
            Paint().apply {
                isAntiAlias = false
                this.color = BORDER_COLOR
                strokeWidth = dividerHeight
            }

        val metaContentLeft = cardLeft + metaPadding
        val metaContentRight = cardRight - metaPadding

        val titleBaselineY = metaCardTop + metaPadding + titleLineHeight * 0.75f
        canvas.drawText(info.title, metaContentLeft, titleBaselineY, titlePaint)

        val valueIconSize = VALUE_ICON_SIZE_DP.dp(context)
        val valueIconToTextGap = VALUE_ICON_TO_TEXT_GAP_DP.dp(context)

        var rowsCursorY = metaCardTop + metaPadding + titleLineHeight + titleToFieldsGap
        info.fields.forEachIndexed { index, field ->
            val textBaselineY = rowsCursorY + fieldLineHeight * 0.75f
            canvas.drawText(field.label, metaContentLeft, textBaselineY, labelPaint)
            val valueTextWidth = valuePaint.measureText(field.value)
            val icon = field.valueIcon
            if (icon != null) {
                val iconPx = valueIconSize.roundToInt().coerceAtLeast(1)
                val scaledIcon = icon.scale(iconPx, iconPx)
                val iconLeft = metaContentRight - valueTextWidth - valueIconToTextGap - iconPx
                val iconTop = rowsCursorY + (fieldLineHeight - iconPx) / 2f
                canvas.drawBitmap(scaledIcon, iconLeft, iconTop, null)
            }
            canvas.drawText(field.value, metaContentRight, textBaselineY, valuePaint)
            rowsCursorY += fieldLineHeight
            if (index < info.fields.lastIndex) {
                rowsCursorY += fieldRowGap
                canvas.drawLine(
                    metaContentLeft,
                    rowsCursorY + dividerHeight / 2f,
                    metaContentRight,
                    rowsCursorY + dividerHeight / 2f,
                    dividerPaint,
                )
                rowsCursorY += dividerHeight + fieldRowGap
            }
        }

        val signatureTop = metaCardBottom + signatureTopGap
        val scaledLogoSize = signatureIconSize.roundToInt().coerceAtLeast(1)
        val scaledLogo = logo.scale(scaledLogoSize, scaledLogoSize)
        val logoLeft = (finalWidth - scaledLogoSize) / 2f
        canvas.drawBitmap(scaledLogo, logoLeft, signatureTop, null)

        val signatureTextPaint =
            Paint().apply {
                isAntiAlias = true
                this.color = TEXT_PRIMARY_COLOR
                textSize = signatureTextSize
                typeface = titleTypeface
                textAlign = Paint.Align.CENTER
            }
        val signatureTextBaseline =
            signatureTop + signatureIconSize + signatureIconToTextGap + signatureTextSize * 0.9f
        canvas.drawText("Vultisig", finalWidth / 2f, signatureTextBaseline, signatureTextPaint)

        return bitmap
    }
}
