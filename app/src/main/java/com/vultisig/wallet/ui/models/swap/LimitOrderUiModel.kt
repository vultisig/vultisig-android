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
    /** Live market price for the reference amount, e.g. "$67,240.00". */
    val marketPriceLabel: String = "",
    /** The computed equivalent of the entered price in the opposite unit, e.g. "0.07902 BTC". */
    val secondaryPriceLabel: String = "",
    val priceUnit: LimitPriceUnit = LimitPriceUnit.Fiat,
    /** Signed distance of the target price from market, e.g. "+2.3%" (null while unknown). */
    val percentFromMarketLabel: String? = null,
    val selectedPreset: LimitPricePreset? = LimitPricePreset.Market,
    val selectedExpiry: LimitExpiryOption = LimitExpiryOption.TwentyFourHours,
    val sellTicker: String = "",
    val sellLogo: ImageModel? = null,
    val buyTicker: String = "",
    val buyLogo: ImageModel? = null,
    /**
     * String resource of the price warning to render (below-market / far-above-market), or null.
     */
    @StringRes val warningRes: Int? = null,
    /**
     * Whether the order has everything it needs to be placed (a target price and a sell amount).
     */
    val isPlaceOrderEnabled: Boolean = false,
)
