package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.MarketWidgetQuery
import java.text.NumberFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Leaderboard widget: the top assets by market cap as one shared surface of separated rows. A 4x2
 * placement shows three rows; anything taller shows five with a header and freshness line.
 */
internal class TopCryptosWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = MarketWidgetEntryPoint.resolve(context)
        val initial = loadModel(context, entryPoint)

        provideContent {
            // Observed rather than loaded once: the refresh worker fills the cache after this
            // session has already started, and a currency change re-prices everything.
            val model by
                produceState(initialValue = initial) {
                    combine(
                            entryPoint.appCurrencyRepository().currency,
                            entryPoint.marketWidgetRepository().version,
                        ) { _, _ ->
                        }
                        .collect { value = loadModel(context, entryPoint) }
                }
            TopCryptosContent(model)
        }
    }

    private suspend fun loadModel(
        context: Context,
        entryPoint: MarketWidgetEntryPoint,
    ): TopCryptosModel {
        val repository = entryPoint.marketWidgetRepository()
        val currencyRepository = entryPoint.appCurrencyRepository()
        val currency = currencyRepository.currency.first()
        val result = repository.cached(MarketWidgetQuery.Top(MAX_ROWS), currency.ticker)
        if (result == null) {
            MarketWidgetRefreshWorker.refreshNow(context, replaceQueued = false)
        }
        return TopCryptosModel(
            assets = result?.assets.orEmpty().map { MarketWidgetAssetUi(it, repository.icon(it)) },
            currencyFormat = currencyRepository.getCurrencyFormat(currency),
            updatedAt = result?.updatedAt ?: 0L,
            isStale = result?.isStale ?: false,
        )
    }

    companion object {
        const val MAX_ROWS = 5
        const val COMPACT_ROWS = 3

        /** Placements shorter than this show the compact three-row list. */
        val LARGE_MIN_HEIGHT = 200.dp
    }
}

// Measured intrinsic heights of the RemoteViews rows (two text lines plus font padding).
private val COMPACT_HEADER_HEIGHT = 18.dp
private val LARGE_HEADER_HEIGHT = 46.dp
private val COMPACT_ROW_HEIGHT = 34.dp
private val LARGE_ROW_HEIGHT = 38.dp

internal class TopCryptosWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TopCryptosWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MarketWidgetRefreshWorker.schedulePeriodic(context)
    }
}

private data class TopCryptosModel(
    val assets: List<MarketWidgetAssetUi>,
    val currencyFormat: NumberFormat,
    val updatedAt: Long,
    val isStale: Boolean,
)

@Composable
private fun TopCryptosContent(model: TopCryptosModel) {
    val (assets, currencyFormat, updatedAt, isStale) = model
    val context = LocalContext.current
    val size = LocalSize.current

    if (assets.isEmpty()) {
        MarketWidgetSurface(
            contentDescription = context.getString(R.string.market_widget_loading)
        ) {
            MarketWidgetLoadingContent()
        }
        return
    }

    val compact = size.height < TopCryptosWidget.LARGE_MIN_HEIGHT
    val showStale = isVisiblyStale(isStale, updatedAt)
    val sparklineWidth = sparklineWidthFor(size.width, compact)
    val showSparkline = sparklineWidth >= MarketWidgetMinSparklineWidth

    // Rows share the height left after the chrome. A launcher can hand us a slot shorter than the
    // spec's minimum, and a clipped fourth row reads worse than three whole ones.
    val chromeHeight =
        MarketWidgetContentPadding * 2 +
            (if (compact) COMPACT_HEADER_HEIGHT else LARGE_HEADER_HEIGHT)
    val rowsThatFit =
        ((size.height - chromeHeight) / (if (compact) COMPACT_ROW_HEIGHT else LARGE_ROW_HEIGHT))
            .toInt()
    val maxRows = if (compact) TopCryptosWidget.COMPACT_ROWS else TopCryptosWidget.MAX_ROWS
    val rows = assets.take(rowsThatFit.coerceIn(1, maxRows))

    MarketWidgetSurface(
        contentDescription = context.getString(R.string.market_widget_top_cryptos_title)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (compact) {
                CompactHeader(showStale, showSparkline)
            } else {
                Header(showStale, showSparkline, updatedLabel(context, updatedAt))
            }

            rows.forEachIndexed { index, ui ->
                if (index > 0) MarketWidgetSeparator()
                MarketWidgetRow(
                    ui = ui,
                    currencyFormat = currencyFormat,
                    compact = compact,
                    sparklineWidth = sparklineWidth,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

/** Three-row list has no title bar; the mark and 7D label sit in a slim strip over the rows. */
@Composable
private fun CompactHeader(showStale: Boolean, showSparkline: Boolean) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (showSparkline) {
            Text(
                text = context.getString(R.string.market_widget_seven_day),
                style = MarketWidgetTheme.label(10.sp, MarketWidgetTheme.tertiaryText),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        MarketWidgetStaleIndicator(showStale)
        MarketWidgetBrandMark(16.dp)
    }
}

/**
 * Title row plus column captions. The freshness time lives up here rather than under the fifth row:
 * RemoteViews rows don't give height back, so a footer is the first thing a tight slot clips.
 */
@Composable
private fun Header(showStale: Boolean, showSparkline: Boolean, updated: String) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Text(
                text = context.getString(R.string.market_widget_top_cryptos_title),
                style = MarketWidgetTheme.label(15.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = updated,
                style = MarketWidgetTheme.label(10.sp, MarketWidgetTheme.tertiaryText),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            MarketWidgetStaleIndicator(showStale)
            MarketWidgetBrandMark(18.dp)
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            val columnStyle = MarketWidgetTheme.label(10.sp, MarketWidgetTheme.tertiaryText)
            Text(
                text = context.getString(R.string.market_widget_asset),
                style = columnStyle,
                maxLines = 1,
                modifier = GlanceModifier.width(MarketWidgetRowIdentityWidth),
            )
            Text(
                text =
                    if (showSparkline) context.getString(R.string.market_widget_seven_day) else "",
                style =
                    TextStyle(
                        color = MarketWidgetTheme.tertiaryText,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = context.getString(R.string.market_widget_price),
                style =
                    TextStyle(
                        color = MarketWidgetTheme.tertiaryText,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.width(MarketWidgetRowValueWidth),
            )
        }
    }
}
