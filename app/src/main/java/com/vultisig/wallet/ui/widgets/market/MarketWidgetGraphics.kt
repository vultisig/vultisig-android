package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.glance.ImageProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.MarketWidgetAsset
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bitmap rendering for the parts of the widgets RemoteViews can't draw natively: the 7-day
 * sparkline and the token icon (downloaded bytes, a bundled vector, or an initial-letter fallback).
 */
internal object MarketWidgetGraphics {

    /**
     * Draws [values] as a line over a soft gradient fill, with a ringed endpoint. The chart uses
     * its own padded min/max domain so a flat stablecoin series stays a centred line. Returns null
     * when there are fewer than two points, so callers can reserve the column without inventing a
     * line.
     */
    fun sparkline(
        context: Context,
        values: List<Double>,
        change: Double?,
        size: DpSize,
        strokeWidth: Dp,
        fillOpacity: Float,
    ): Bitmap? {
        if (values.size < 2) return null
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt().coerceIn(1, MAX_BITMAP_EDGE_PX)
        val height = (size.height.value * density).roundToInt().coerceIn(1, MAX_BITMAP_EDGE_PX)
        val stroke = strokeWidth.value * density
        val tint = MarketWidgetTheme.changeColor(change).toArgb()

        val minimum = values.min()
        val maximum = values.max()
        val span = maximum - minimum
        val padding = if (span == 0.0) max(kotlin.math.abs(minimum) * 0.05, 1.0) else span * 0.08
        val lower = minimum - padding
        val domain = (maximum + padding) - lower

        // Inset by the endpoint radius so the ring and stroke caps are never clipped.
        val inset = ENDPOINT_RADIUS_DP * density + stroke
        val drawWidth = width - inset * 2
        val drawHeight = height - inset * 2
        val xInterval = drawWidth / (values.size - 1)
        val points =
            values.mapIndexed { index, value ->
                val normalized = ((value - lower) / domain).toFloat()
                inset + index * xInterval to inset + drawHeight * (1f - normalized)
            }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val line =
            Path().apply {
                moveTo(points.first().first, points.first().second)
                points.drop(1).forEach { (x, y) -> lineTo(x, y) }
            }
        val area =
            Path(line).apply {
                lineTo(points.last().first, height.toFloat())
                lineTo(points.first().first, height.toFloat())
                close()
            }

        canvas.drawPath(
            area,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        Color(tint).copy(alpha = fillOpacity).toArgb(),
                        Color(tint).copy(alpha = 0f).toArgb(),
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = tint
                this.strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )

        val (endX, endY) = points.last()
        val endpointRadius = ENDPOINT_RADIUS_DP * density
        canvas.drawCircle(
            endX,
            endY,
            endpointRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = tint
            },
        )
        canvas.drawCircle(
            endX,
            endY,
            endpointRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = MarketWidgetTheme.primaryTextColor.toArgb()
                this.strokeWidth = ENDPOINT_RING_DP * density
            },
        )
        return bitmap
    }

    /**
     * Icon for [asset]: the downloaded CoinGecko image when the cache has it, else a bundled logo
     * for the handful of majors we ship, else a deterministic initial-letter disc.
     */
    fun icon(
        context: Context,
        asset: MarketWidgetAsset,
        iconBytes: ByteArray?,
        size: Dp,
    ): ImageProvider {
        val density = context.resources.displayMetrics.density
        val px = (size.value * density).roundToInt().coerceAtLeast(1)

        iconBytes
            ?.let { decodeIcon(it, px) }
            ?.let {
                return ImageProvider(it)
            }
        bundledIcon(asset.id)?.let {
            return ImageProvider(it)
        }
        return ImageProvider(initialDisc(asset.symbol, px))
    }

    private fun bundledIcon(id: String): Int? =
        when (id) {
            "bitcoin" -> R.drawable.bitcoin
            "ethereum" -> R.drawable.ethereum
            "tether" -> R.drawable.usdt
            "binancecoin" -> R.drawable.bsc
            "solana" -> R.drawable.solana
            else -> null
        }

    private fun decodeIcon(bytes: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return Bitmap.createScaledBitmap(decoded, px, px, true)
    }

    private fun initialDisc(symbol: String, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = px / 2f
        canvas.drawCircle(
            radius,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarketWidgetTheme.primaryTextColor.toArgb()
            },
        )
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MarketWidgetTheme.background.toArgb()
                textSize = px * 0.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
        val initial = symbol.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val baseline = radius - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(initial, radius, baseline, paint)
        return bitmap
    }

    private const val ENDPOINT_RADIUS_DP = 2.5f
    private const val ENDPOINT_RING_DP = 1.5f
    // RemoteViews carries every bitmap through Binder; keep charts well under the per-widget cap.
    private const val MAX_BITMAP_EDGE_PX = 900
}
