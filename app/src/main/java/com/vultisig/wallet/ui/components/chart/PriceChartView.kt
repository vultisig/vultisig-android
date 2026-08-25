package com.vultisig.wallet.ui.components.chart

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ChartPointUiModel
import com.vultisig.wallet.ui.models.ChartUiModel
import com.vultisig.wallet.ui.theme.Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val CHART_HEIGHT = 168.dp

/**
 * Reserved height for the scrub date under the price. The label appears and disappears on every
 * drag, and a header that changes height would make the chart jump under the finger.
 */
private val SCRUB_CAPTION_HEIGHT = 16.dp

/** Opacity a superseded series is held at while the next range loads. */
private const val DIMMED_ALPHA = 0.3f

/**
 * The price history card: spot price, period-change chip, the series itself and the range picker.
 * Scrubbing the series swaps the price for the scrubbed one and dates it.
 */
@Composable
internal fun PriceChartSection(
    chart: ChartUiModel,
    spotPriceText: String?,
    onRangeSelected: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbedPoint by remember(chart.points) { mutableStateOf<ChartPointUiModel?>(null) }

    // A range switch keeps the previous series on screen while the next resolves; the change chip
    // still describes the old window, so the two are dimmed together as one stale group rather
    // than the percentage reading as a live figure for the range already highlighted below.
    val isDimmed = chart.isLoading && chart.points.isNotEmpty()

    TokenDetailCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(all = 16.dp)) {
            ChartHeader(
                isPositive = chart.isPositive,
                changePercentText = chart.changePercentText,
                priceText = scrubbedPoint?.priceText ?: spotPriceText,
                scrubbedPoint = scrubbedPoint,
                selectedRange = chart.selectedRange,
                isDimmed = isDimmed,
            )
            UiSpacer(size = 16.dp)
            PriceChartCanvas(
                points = chart.points,
                isPositive = chart.isPositive,
                isLoading = chart.isLoading,
                isStale = chart.isStale,
                changePercentText = chart.changePercentText,
                onScrub = { scrubbedPoint = it },
                onRetry = { onRangeSelected(chart.selectedRange) },
            )
            UiSpacer(size = 16.dp)
            ChartRangePicker(selectedRange = chart.selectedRange, onRangeSelected = onRangeSelected)
        }
    }
}

@Composable
private fun ChartHeader(
    isPositive: Boolean,
    changePercentText: String,
    priceText: String?,
    scrubbedPoint: ChartPointUiModel?,
    selectedRange: ChartRange,
    isDimmed: Boolean,
) {
    val tint = if (isPositive) Theme.v2.colors.alerts.success else Theme.v2.colors.alerts.error
    // LocalLocale, not Locale.current or LocalConfiguration: only the composition local is an
    // observed read, so a per-app language change re-formats an already-composed scrub caption
    // instead of leaving it in the previous locale.
    val locale = LocalLocale.current.platformLocale

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Text(
                text = priceText.orEmpty(),
                style = Theme.satoshi.price.title2,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 2.dp)
            Box(modifier = Modifier.height(SCRUB_CAPTION_HEIGHT)) {
                if (scrubbedPoint != null) {
                    Text(
                        text = scrubbedPoint.timestampMillis.toScrubDateText(selectedRange, locale),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }
        }

        UiSpacer(weight = 1f)

        if (changePercentText.isNotEmpty()) {
            Text(
                text = changePercentText,
                style = Theme.brockmann.supplementary.caption,
                color = tint,
                modifier =
                    Modifier.alpha(if (isDimmed) DIMMED_ALPHA else 1f)
                        .clip(Theme.v2.radius.pill)
                        .background(tint.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Line/fill geometry for one (points, tint, canvas size) combination, rebuilt only when one of
 * those actually changes rather than on every scrub-drag frame.
 */
private class ChartGeometry(
    val stepX: Float,
    val minPrice: Double,
    val priceRange: Double,
    val heightPx: Float,
    val linePath: Path,
    val fillPath: Path,
    val fillBrush: Brush,
) {
    fun yFor(price: Double): Float = yForPrice(price, minPrice, priceRange, heightPx)
}

private fun yForPrice(price: Double, minPrice: Double, priceRange: Double, heightPx: Float): Float =
    heightPx - ((price - minPrice) / priceRange * heightPx).toFloat()

private fun buildChartGeometry(
    points: List<ChartPointUiModel>,
    tint: Color,
    size: IntSize,
): ChartGeometry? {
    if (points.size < 2 || size.width == 0 || size.height == 0) return null
    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
    val heightPx = size.height.toFloat()
    val stepX = size.width / (points.size - 1).toFloat()

    val linePath =
        Path().apply {
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = yForPrice(point.price, minPrice, priceRange, heightPx)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
    val fillPath =
        Path().apply {
            addPath(linePath)
            lineTo(size.width.toFloat(), heightPx)
            lineTo(0f, heightPx)
            close()
        }
    val fillBrush =
        Brush.verticalGradient(colors = listOf(tint.copy(alpha = 0.28f), Color.Transparent))
    return ChartGeometry(stepX, minPrice, priceRange, heightPx, linePath, fillPath, fillBrush)
}

@Composable
private fun PriceChartCanvas(
    points: List<ChartPointUiModel>,
    isPositive: Boolean,
    isLoading: Boolean,
    isStale: Boolean,
    changePercentText: String,
    onScrub: (ChartPointUiModel?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (isPositive) Theme.v2.colors.alerts.success else Theme.v2.colors.alerts.error
    val markerCenterColor = Theme.v2.colors.backgrounds.primary
    val placeholderColor = Theme.v2.colors.backgrounds.tertiary_2
    val chartDescription =
        stringResource(R.string.price_chart_content_description, changePercentText.ifEmpty { "—" })

    Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        when {
            points.size >= 2 -> {
                var scrubX by remember(points) { mutableStateOf<Float?>(null) }
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                val latestPoints by rememberUpdatedState(points)
                val latestOnScrub by rememberUpdatedState(onScrub)

                fun updateScrub(x: Float, width: Float) {
                    val current = latestPoints
                    scrubX = x
                    latestOnScrub(current[nearestPointIndex(x, width, current.size)])
                }

                val geometry =
                    remember(points, tint, canvasSize) {
                        buildChartGeometry(points, tint, canvasSize)
                    }

                Canvas(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(CHART_HEIGHT)
                            // A range/currency-switch refetch keeps the previous series on screen
                            // while it resolves; dim it so isLoading still gives feedback instead
                            // of looking finished.
                            .alpha(if (isLoading) DIMMED_ALPHA else 1f)
                            .semantics { contentDescription = chartDescription }
                            .onSizeChanged { canvasSize = it }
                            // Keyed on Unit (not points) so a mid-scrub data refresh (e.g. a
                            // currency switch) doesn't cancel the running drag gesture.
                            .pointerInput(Unit) {
                                // Horizontal-only: an omnidirectional detector claims the pointer
                                // on vertical slop too, which would swallow the drags that scroll
                                // this screen and move the sheet it is presented in.
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        updateScrub(offset.x, size.width.toFloat())
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        updateScrub(change.position.x, size.width.toFloat())
                                    },
                                    onDragEnd = {
                                        scrubX = null
                                        latestOnScrub(null)
                                    },
                                    onDragCancel = {
                                        scrubX = null
                                        latestOnScrub(null)
                                    },
                                )
                            }
                ) {
                    val g = geometry ?: return@Canvas

                    drawPath(path = g.fillPath, brush = g.fillBrush)
                    drawPath(
                        path = g.linePath,
                        color = tint,
                        style =
                            Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )

                    scrubX?.let { x ->
                        val index = nearestPointIndex(x, size.width, points.size)
                        val markerX = index * g.stepX
                        val markerY = g.yFor(points[index].price)
                        drawLine(
                            color = tint.copy(alpha = 0.4f),
                            start = Offset(markerX, 0f),
                            end = Offset(markerX, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawCircle(
                            color = tint,
                            radius = 5.dp.toPx(),
                            center = Offset(markerX, markerY),
                        )
                        drawCircle(
                            color = markerCenterColor,
                            radius = 2.5.dp.toPx(),
                            center = Offset(markerX, markerY),
                        )
                    }
                }
            }

            isLoading -> {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(CHART_HEIGHT)
                            .clip(Theme.v2.radius.md)
                            .background(placeholderColor)
                )
            }

            // A range switch that fails before any data has ever loaded for it lands here
            // (isStale=true, points cleared) — surface a retry affordance rather than a
            // silent blank box, since nothing else on screen signals the fetch failed.
            isStale -> {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(CHART_HEIGHT)
                            .clip(Theme.v2.radius.md)
                            .background(placeholderColor)
                            .clickable(onClick = onRetry),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.price_chart_retry_hint),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }

            else -> Unit
        }
    }
}

private fun nearestPointIndex(x: Float, width: Float, count: Int): Int {
    if (count <= 1) return 0
    val stepX = width / (count - 1)
    return (x / stepX).roundToInt().coerceIn(0, count - 1)
}

/**
 * Dates a scrubbed point at the resolution its window actually resolves: a time of day says nothing
 * on the ALL series, and a year says nothing on the 1D one.
 */
private fun Long.toScrubDateText(range: ChartRange, locale: Locale): String {
    val skeleton =
        when (range) {
            ChartRange.ONE_DAY -> "jm"
            ChartRange.ONE_WEEK,
            ChartRange.ONE_MONTH -> "MMMdjm"
            ChartRange.ONE_YEAR,
            ChartRange.ALL -> "yMMMd"
        }
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
}

@Preview
@Composable
private fun PriceChartSectionPreview() {
    val points =
        (0 until 48).map { i ->
            ChartPointUiModel(
                timestampMillis = i * 3_600_000L,
                price = 100.0 + kotlin.math.sin(i / 4.0) * 10,
                priceText = "$${100 + i}",
            )
        }
    PriceChartSection(
        chart =
            ChartUiModel(
                selectedRange = ChartRange.ONE_DAY,
                points = points,
                isPositive = true,
                changePercentText = "+3.24%",
            ),
        spotPriceText = "$1,850.92",
        onRangeSelected = {},
    )
}
