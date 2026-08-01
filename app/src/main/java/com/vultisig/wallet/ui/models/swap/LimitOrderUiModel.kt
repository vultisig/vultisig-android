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
    /**
     * What [referenceAmountLabel]'s worth of the sell asset fetches at the target price, in the
     * emphasized unit — e.g. "$2.15" (fiat) or "12.76050795 DOGE" (asset).
     */
    val priceText: String = "",
    /**
     * e.g. "5 RUNE" — the sell amount the two figures below it are quoted for, so all three lines
     * describe one quantity. Falls back to one whole unit before an amount is entered.
     */
    val referenceAmountLabel: String = "",
    val referenceLogo: ImageModel? = null,
    /** The same value as [priceText] in the other unit, e.g. "12.76050795 DOGE" next to "$2.15". */
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
