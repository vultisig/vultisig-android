package com.vultisig.wallet.ui.models.swap

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel

/** Preset target-price pills: Market price, or a percentage above it. */
internal enum class LimitPricePreset(val labelRes: Int, val percentAboveMarket: Int) {
    Market(R.string.limit_swap_preset_market, 0),
    Plus1(R.string.limit_swap_preset_plus_1, 1),
    Plus5(R.string.limit_swap_preset_plus_5, 5),
    Plus10(R.string.limit_swap_preset_plus_10, 10),
}

/** Order lifetime pills. [hours] feeds the memo interval; default is 24h per spec. */
internal enum class LimitExpiryOption(val hours: Int, val labelRes: Int) {
    TwelveHours(12, R.string.limit_swap_expiry_12h),
    TwentyFourHours(24, R.string.limit_swap_expiry_24h),
    ThreeDays(72, R.string.limit_swap_expiry_3d),
}

/**
 * The two collapsible sections of the limit form. Exactly one is expanded at a time: opening either
 * collapses the other to its summary row.
 */
internal enum class LimitFormSection {
    ExecuteWhen,
    Asset,
}

/** Which unit the big price number is shown in. The underlying LIM math is unit-agnostic. */
internal enum class LimitPriceUnit {
    Fiat,
    Asset,
}

/**
 * UI state for the THORChain limit-order ("Execute when") form. Purely presentational — the price
 * math, market-price probe, and warnings are computed in the ViewModel.
 */
@Immutable
internal data class LimitOrderUiModel(
    /** The large target-price number as shown, e.g. "$65,800.13" (fiat unit) or "0.07902 BTC". */
    val priceText: String = "",
    /** e.g. "1 BTC" — the buy asset the target price is quoted per. */
    val referenceAmountLabel: String = "",
    val referenceLogo: ImageModel? = null,
    /** The computed equivalent of the entered price in the opposite unit, e.g. "0.07902 BTC". */
    val secondaryPriceLabel: String = "",
    val priceUnit: LimitPriceUnit = LimitPriceUnit.Fiat,
    val selectedPreset: LimitPricePreset? = LimitPricePreset.Market,
    val selectedExpiry: LimitExpiryOption = LimitExpiryOption.TwentyFourHours,
    val sellTicker: String = "",
    val sellLogo: ImageModel? = null,
    val buyTicker: String = "",
    val buyLogo: ImageModel? = null,
    /**
     * Buy-leg amount the entered sell amount would yield at the target price, e.g. "0.0790275".
     * Shown in the expanded asset editor; "0" until both a price and a sell amount are known.
     */
    val buyAmountText: String = "0",
    /**
     * String resource of the price warning to render (below-market / far-above-market), or null.
     */
    @StringRes val warningRes: Int? = null,
    /**
     * Whether the order has everything it needs to be placed (a target price and a sell amount).
     */
    val isPlaceOrderEnabled: Boolean = false,
)
