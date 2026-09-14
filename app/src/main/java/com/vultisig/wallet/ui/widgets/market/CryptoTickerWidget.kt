package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.MarketWidgetAssetIdentity
import com.vultisig.wallet.data.models.MarketWidgetQuery
import java.text.NumberFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Single-asset price widget. A 2x2 cell shows icon, identity, price and 24h change; a wider
 * placement adds the seven-day sparkline. The asset is chosen in [CryptoTickerConfigureActivity]
 * and stored in this widget's Glance preferences; Bitcoin until then.
 */
internal class CryptoTickerWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = MarketWidgetEntryPoint.resolve(context)
        // Glance's `compose` preview path hands out a fake id with no backing state; fall back
        // to the default asset rather than failing the whole render.
        val prefs =
            runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, id) }
                .getOrNull() ?: emptyPreferences()
        val initial = loadModel(context, entryPoint, selectedAssetId(prefs))

        provideContent {
            // The asset can change through the configure activity while this session is alive,
            // and the cache/currency change underneath it, so everything is observed here rather
            // than loaded once above.
            val assetId = selectedAssetId(currentState())
            val model by
                produceState(initialValue = initial, key1 = assetId) {
                    combine(
                            entryPoint.appCurrencyRepository().currency,
                            entryPoint.marketWidgetRepository().version,
                        ) { _, _ ->
                        }
                        .collect { value = loadModel(context, entryPoint, assetId) }
                }
            CryptoTickerContent(model)
        }
    }

    private suspend fun loadModel(
        context: Context,
        entryPoint: MarketWidgetEntryPoint,
        assetId: String,
    ): CryptoTickerModel {
        val repository = entryPoint.marketWidgetRepository()
        val currencyRepository = entryPoint.appCurrencyRepository()
        val currency = currencyRepository.currency.first()
        val result = repository.cached(MarketWidgetQuery.Ids(listOf(assetId)), currency.ticker)
        val asset = result?.assets?.firstOrNull()
        if (result == null) {
            MarketWidgetRefreshWorker.refreshNow(context, replaceQueued = false)
        }
        return CryptoTickerModel(
            ui = asset?.let { MarketWidgetAssetUi(asset = it, iconBytes = repository.icon(it)) },
            currencyFormat = currencyRepository.getCurrencyFormat(currency),
            updatedAt = result?.updatedAt ?: 0L,
            isStale = result?.isStale ?: false,
        )
    }

    companion object {
        val KEY_ASSET_ID = stringPreferencesKey("asset_id")
        val KEY_ASSET_SYMBOL = stringPreferencesKey("asset_symbol")
        val KEY_ASSET_NAME = stringPreferencesKey("asset_name")

        val DEFAULT_ASSET = MarketWidgetAssetIdentity("bitcoin", "BTC", "Bitcoin")

        /** Placements narrower than this drop the sparkline. */
        val MEDIUM_MIN_WIDTH = 200.dp

        fun selectedAssetId(prefs: Preferences): String =
            prefs[KEY_ASSET_ID]?.takeIf { it.isNotBlank() } ?: DEFAULT_ASSET.id
    }
}

internal class CryptoTickerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CryptoTickerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MarketWidgetRefreshWorker.schedulePeriodic(context)
    }
}

private data class CryptoTickerModel(
    val ui: MarketWidgetAssetUi?,
    val currencyFormat: NumberFormat,
    val updatedAt: Long,
    val isStale: Boolean,
)

@Composable
private fun CryptoTickerContent(model: CryptoTickerModel) {
    val (ui, currencyFormat, updatedAt, isStale) = model
    val context = LocalContext.current
    val size = LocalSize.current

    if (ui == null) {
        MarketWidgetSurface(
            contentDescription = context.getString(R.string.market_widget_loading)
        ) {
            MarketWidgetLoadingContent()
        }
        return
    }

    val asset = ui.asset
    val price = MarketWidgetFormatting.price(context, asset.currentPrice, currencyFormat)
    val showStale = isVisiblyStale(isStale, updatedAt)
    val description =
        MarketWidgetFormatting.accessibility(
            context = context,
            name = asset.name,
            symbol = asset.symbol,
            price = price,
            change = asset.finiteChange24h,
        )

    MarketWidgetSurface(contentDescription = description) {
        if (size.width >= CryptoTickerWidget.MEDIUM_MIN_WIDTH) {
            MediumContent(ui, price, showStale, updatedAt)
        } else {
            SmallContent(ui, price, showStale, updatedAt)
        }
    }
}

@Composable
private fun SmallContent(
    ui: MarketWidgetAssetUi,
    price: String,
    showStale: Boolean,
    updatedAt: Long,
) {
    val asset = ui.asset
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            MarketWidgetTokenIcon(ui, 28.dp)
            Spacer(modifier = GlanceModifier.width(8.dp))
            MarketWidgetIdentity(
                asset = asset,
                symbolSize = 14.sp,
                nameSize = 11.sp,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            MarketWidgetStaleIndicator(showStale)
            MarketWidgetBrandMark(18.dp)
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(text = price, style = MarketWidgetTheme.label(21.sp), maxLines = 1)
        MarketWidgetChangeText(change = asset.finiteChange24h, size = 12.sp)
        if (showStale) {
            Text(
                text = updatedLabel(LocalContext.current, updatedAt),
                style = MarketWidgetTheme.label(10.sp, MarketWidgetTheme.tertiaryText),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MediumContent(
    ui: MarketWidgetAssetUi,
    price: String,
    showStale: Boolean,
    updatedAt: Long,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val asset = ui.asset
    val hasSparkline = asset.sparkline.size > 1

    // Everything above the chart: header row, value row and their spacing. The chart takes what
    // is left, and disappears rather than shrinking into a smear when that is under 24dp.
    val chartHeight =
        size.height -
            MarketWidgetContentPadding * 2 -
            30.dp -
            8.dp -
            26.dp -
            8.dp -
            (if (showStale) 14.dp else 0.dp)
    val chartWidth = size.width - MarketWidgetContentPadding * 2

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            MarketWidgetTokenIcon(ui, 30.dp)
            Spacer(modifier = GlanceModifier.width(8.dp))
            MarketWidgetIdentity(
                asset = asset,
                symbolSize = 14.sp,
                nameSize = 11.sp,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (hasSparkline) {
                Text(
                    text = context.getString(R.string.market_widget_seven_day),
                    style = MarketWidgetTheme.label(11.sp, MarketWidgetTheme.secondaryText),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
            }
            MarketWidgetStaleIndicator(showStale)
            MarketWidgetBrandMark(18.dp)
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.Vertical.Bottom,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Text(text = price, style = MarketWidgetTheme.label(22.sp), maxLines = 1)
            Spacer(modifier = GlanceModifier.width(12.dp))
            MarketWidgetChangeText(change = asset.finiteChange24h, size = 12.sp)
        }

        if (chartHeight >= 24.dp) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            MarketWidgetSparkline(
                asset = asset,
                size = DpSize(chartWidth, chartHeight),
                strokeWidth = 2.dp,
                fillOpacity = 0.28f,
            )
        }

        if (showStale) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = updatedLabel(context, updatedAt),
                style = MarketWidgetTheme.label(10.sp, MarketWidgetTheme.tertiaryText),
                maxLines = 1,
            )
        }
    }
}
