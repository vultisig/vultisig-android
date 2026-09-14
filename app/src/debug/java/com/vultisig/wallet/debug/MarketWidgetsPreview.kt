package com.vultisig.wallet.debug

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.vultisig.wallet.ui.widgets.market.CryptoTickerWidget
import com.vultisig.wallet.ui.widgets.market.MarketWidgetEntryPoint
import com.vultisig.wallet.ui.widgets.market.TopCryptosWidget

/**
 * Renders the market widgets at each supported slot size through Glance's `compose`, so every
 * layout variant can be screenshotted without wrestling a launcher's resize handles. Data comes
 * from the real repository cache — place a widget (or run the refresh worker) first.
 */
@Composable
internal fun MarketWidgetsPreview() {
    val ticker = CryptoTickerWidget()
    val top = TopCryptosWidget()
    val bitcoin = mutablePreferencesOf(CryptoTickerWidget.KEY_ASSET_ID to "bitcoin")

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxSize()
                .background(Color(0xFF202020))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        WidgetSlot(ticker, DpSize(155.dp, 155.dp), bitcoin)
        WidgetSlot(ticker, DpSize(330.dp, 155.dp), bitcoin)
        WidgetSlot(top, DpSize(330.dp, 155.dp), null)
        WidgetSlot(top, DpSize(330.dp, 330.dp), null)
    }
}

@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetSlot(widget: GlanceAppWidget, size: DpSize, state: Any?) {
    val context = LocalContext.current
    // `compose` is a one-shot snapshot, so re-run it whenever the cache or currency moves —
    // otherwise a preview opened on an empty cache stays on its loading state.
    val entryPoint = remember(context) { MarketWidgetEntryPoint.resolve(context) }
    val version by entryPoint.marketWidgetRepository().version.collectAsState()
    val currency by entryPoint.appCurrencyRepository().currency.collectAsState(initial = null)
    val remoteViews by
        produceState<RemoteViews?>(initialValue = null, version, currency) {
            value = widget.compose(context = context, size = size, state = state)
        }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            frame.removeAllViews()
            remoteViews?.apply(frame.context, frame)?.let {
                // Hosts size the widget root to the slot; mirror that or weights don't apply.
                frame.addView(
                    it,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        modifier = Modifier.size(size),
    )
}
