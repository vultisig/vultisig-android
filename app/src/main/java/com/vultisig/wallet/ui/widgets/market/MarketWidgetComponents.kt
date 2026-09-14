package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.models.MarketWidgetAsset
import java.text.NumberFormat
import java.util.Date

/** Padding between the widget edge and its content, applied by [MarketWidgetSurface]. */
internal val MarketWidgetContentPadding = 12.dp

/** How old a snapshot may get before the widget starts flagging it visibly. */
internal const val MARKET_WIDGET_STALE_AFTER_MS = 2L * 60 * 60 * 1000

/** Everything a widget needs to draw one asset, resolved outside composition. */
internal data class MarketWidgetAssetUi(val asset: MarketWidgetAsset, val iconBytes: ByteArray?)

/** Rounded navy container that fills the widget and opens the app on tap. */
@Composable
internal fun MarketWidgetSurface(contentDescription: String, content: @Composable () -> Unit) {
    Box(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(ImageProvider(R.drawable.bg_market_widget))
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(MarketWidgetContentPadding)
                .semantics { this.contentDescription = contentDescription }
    ) {
        content()
    }
}

@Composable
internal fun MarketWidgetBrandMark(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_market_widget_logo_outline),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/** Small clock glyph shown next to the brand mark once the snapshot is stale. */
@Composable
internal fun MarketWidgetStaleIndicator(visible: Boolean) {
    if (!visible) return
    Image(
        provider = ImageProvider(R.drawable.ic_market_widget_stale),
        contentDescription = LocalContext.current.getString(R.string.market_widget_cached),
        modifier = GlanceModifier.size(12.dp).padding(end = 6.dp),
    )
}

@Composable
internal fun MarketWidgetTokenIcon(ui: MarketWidgetAssetUi, size: Dp) {
    Image(
        provider = MarketWidgetGraphics.icon(LocalContext.current, ui.asset, ui.iconBytes, size),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/** Ticker over full name; the ticker survives first when the column is squeezed. */
@Composable
internal fun MarketWidgetIdentity(
    asset: MarketWidgetAsset,
    symbolSize: TextUnit,
    nameSize: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(text = asset.symbol, style = MarketWidgetTheme.label(symbolSize), maxLines = 1)
        Text(
            text = asset.name,
            style = MarketWidgetTheme.label(nameSize, MarketWidgetTheme.secondaryText),
            maxLines = 1,
        )
    }
}

@Composable
internal fun MarketWidgetChangeText(
    change: Double?,
    size: TextUnit,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Text(
        text = MarketWidgetFormatting.change(context, change),
        style =
            TextStyle(
                color = ColorProvider(MarketWidgetTheme.changeColor(change)),
                fontSize = size,
                fontWeight = FontWeight.Medium,
                textAlign = align,
            ),
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Sparkline slot. Reserves [size] even when there is nothing to draw, so rows stay aligned and a
 * missing chart never shifts the price column.
 */
@Composable
internal fun MarketWidgetSparkline(
    asset: MarketWidgetAsset,
    size: DpSize,
    strokeWidth: Dp,
    fillOpacity: Float,
    modifier: GlanceModifier = GlanceModifier,
) {
    val bitmap =
        MarketWidgetGraphics.sparkline(
            context = LocalContext.current,
            values = asset.sparkline,
            change = asset.finiteChange24h,
            size = size,
            strokeWidth = strokeWidth,
            fillOpacity = fillOpacity,
        )
    if (bitmap == null) {
        Spacer(modifier = modifier.width(size.width).height(size.height))
    } else {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = modifier.width(size.width).height(size.height),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * One list row: icon + identity, sparkline (when [sparklineWidth] leaves room), then price over 24h
 * change, trailing-aligned. Used by the Top Cryptos widget in both sizes.
 */
@Composable
internal fun MarketWidgetRow(
    ui: MarketWidgetAssetUi,
    currencyFormat: NumberFormat,
    compact: Boolean,
    sparklineWidth: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val asset = ui.asset
    val price = MarketWidgetFormatting.price(context, asset.currentPrice, currencyFormat)
    val description =
        MarketWidgetFormatting.accessibility(
            context = context,
            name = asset.name,
            symbol = asset.symbol,
            price = price,
            change = asset.finiteChange24h,
        )
    val iconSize = if (compact) 28.dp else 30.dp
    val spacing = if (compact) 8.dp else 10.dp

    Row(
        verticalAlignment = Alignment.Vertical.CenterVertically,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.width(MarketWidgetRowIdentityWidth),
        ) {
            MarketWidgetTokenIcon(ui, iconSize)
            Spacer(modifier = GlanceModifier.width(spacing))
            MarketWidgetIdentity(
                asset = asset,
                symbolSize = if (compact) 13.sp else 14.sp,
                nameSize = if (compact) 10.sp else 11.sp,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        if (sparklineWidth >= MarketWidgetMinSparklineWidth) {
            Spacer(modifier = GlanceModifier.width(spacing))
            MarketWidgetSparkline(
                asset = asset,
                size = DpSize(sparklineWidth, if (compact) 28.dp else 34.dp),
                strokeWidth = if (compact) 1.5.dp else 1.7.dp,
                fillOpacity = 0.2f,
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())

        Column(
            horizontalAlignment = Alignment.Horizontal.End,
            modifier = GlanceModifier.width(MarketWidgetRowValueWidth),
        ) {
            Text(
                text = price,
                style =
                    TextStyle(
                        color = MarketWidgetTheme.primaryText,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
            MarketWidgetChangeText(
                change = asset.finiteChange24h,
                size = 10.sp,
                align = TextAlign.End,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}

/** First render after placement, before the refresh worker has filled the cache. */
@Composable
internal fun MarketWidgetLoadingContent() {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Vertical.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Text(
                text = context.getString(R.string.market_widget_market_data),
                style = MarketWidgetTheme.label(14.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            MarketWidgetBrandMark(18.dp)
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = context.getString(R.string.market_widget_loading),
            style = MarketWidgetTheme.label(12.sp, MarketWidgetTheme.secondaryText),
            maxLines = 2,
        )
    }
}

@Composable
internal fun MarketWidgetSeparator() {
    Spacer(
        modifier =
            GlanceModifier.fillMaxWidth().height(1.dp).background(MarketWidgetTheme.separator)
    )
}

/** Absolute "Updated 14:05" — relative wording would drift between 30-minute re-renders. */
internal fun updatedLabel(context: Context, updatedAt: Long): String =
    context.getString(
        R.string.market_widget_updated_at,
        DateFormat.getTimeFormat(context).format(Date(updatedAt)),
    )

internal fun isVisiblyStale(isStale: Boolean, updatedAt: Long): Boolean =
    isStale || System.currentTimeMillis() - updatedAt > MARKET_WIDGET_STALE_AFTER_MS

/** Width of the icon + identity column in list rows. */
internal val MarketWidgetRowIdentityWidth = 96.dp

/** Width of the trailing price / change column in list rows. */
internal val MarketWidgetRowValueWidth = 88.dp

/** Below this the chart is noise; hide it and let the identity and value columns breathe. */
internal val MarketWidgetMinSparklineWidth = 40.dp

/** Width left for a row's sparkline after the fixed columns and their spacing. */
internal fun sparklineWidthFor(widgetWidth: Dp, compact: Boolean): Dp {
    val spacing = if (compact) 8.dp else 10.dp
    return widgetWidth -
        MarketWidgetContentPadding * 2 -
        MarketWidgetRowIdentityWidth -
        MarketWidgetRowValueWidth -
        spacing * 2
}
