package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import android.text.format.DateUtils
import com.vultisig.wallet.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Text rendering rules shared by both market widgets. */
internal object MarketWidgetFormatting {

    /**
     * Price with fraction digits scaled to magnitude — two for anything ≥ 1, up to four below that,
     * and up to eight for sub-cent tokens — so a micro-cap doesn't collapse to "$0.00".
     * [currencyFormat] carries the selected currency's symbol and the locale's separators.
     */
    fun price(context: Context, value: Double, currencyFormat: NumberFormat): String {
        if (!value.isFinite()) return context.getString(R.string.market_widget_value_unavailable)
        val format = currencyFormat.clone() as NumberFormat
        format.isGroupingUsed = true
        format.minimumFractionDigits = 2
        format.maximumFractionDigits =
            when {
                abs(value) >= 1 -> 2
                abs(value) >= 0.01 -> 4
                else -> 8
            }
        return format.format(value)
    }

    /** "+3.54% 24H", "-1.25% 24H", or the neutral "— 24H" when there is no usable change. */
    fun change(context: Context, value: Double?): String {
        if (value == null || !value.isFinite()) {
            return context.getString(R.string.market_widget_change_unavailable)
        }
        val percent =
            NumberFormat.getPercentInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        val formatted = percent.format(abs(value) / 100)
        val signed = if (value >= 0) "+$formatted" else "-$formatted"
        return context.getString(R.string.market_widget_change_24h, signed)
    }

    /** "Updated 5 min. ago" style relative timestamp for the freshness line. */
    fun updated(context: Context, updatedAtMillis: Long): String {
        val relative =
            DateUtils.getRelativeTimeSpanString(
                updatedAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
        return context.getString(R.string.market_widget_updated_at, relative)
    }

    /** Spoken summary of one asset for screen readers; mirrors the visible hierarchy. */
    fun accessibility(
        context: Context,
        name: String,
        symbol: String,
        price: String,
        change: Double?,
    ): String {
        val spokenChange =
            if (change == null) {
                context.getString(R.string.market_widget_change_unavailable_spoken)
            } else {
                val direction =
                    context.getString(
                        if (change >= 0) R.string.market_widget_up else R.string.market_widget_down
                    )
                "$direction ${change(context, change)}"
            }
        return context.getString(
            R.string.market_widget_accessibility_asset,
            name,
            symbol,
            price,
            spokenChange,
        )
    }
}
