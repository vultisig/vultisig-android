package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.utils.UiText
import kotlinx.datetime.Instant

/**
 * Destination-side quote display values, shown while a quote loads and after it resolves.
 *
 * @property isDstEstimated True while the shown destination value is an indicative spot-price
 *   estimate (rendered greyed) rather than a firm provider quote. Display-only — never gates
 *   Continue (#4712).
 */
internal data class QuoteDisplay(
    val provider: UiText = UiText.Empty,
    val estimatedDstTokenValue: String = "0",
    val estimatedDstFiatValue: String = "0",
    val isDstEstimated: Boolean = false,
    val hasQuote: Boolean = false,
    val expiredAt: Instant? = null,
)

/** Network and swap fee breakdown rendered in the fee-details panel. */
internal data class FeeBreakdown(
    val networkFee: String = "",
    val networkFeeFiat: String = "",
    val totalFee: String = "0",
    val fee: String = "",
    val outboundFee: String? = null,
    val swapFeePercent: String? = null,
)

/** VULT-tier and referral discount info rendered in the fee-details panel. */
internal data class DiscountInfo(
    val tierType: TierType? = null,
    val vultBpsDiscount: Int? = null,
    val vultBpsDiscountFiatValue: String? = null,
    val referralBpsDiscount: Int? = null,
    val referralBpsDiscountFiatValue: String? = null,
)

/**
 * Aggregated swap-form state, grouping the quote, fee, and discount details into their respective
 * sub-models ([quoteDisplay], [feeBreakdown], [discountInfo]) alongside the selected tokens,
 * errors, and loading/enablement flags rendered by the swap screen.
 */
internal data class SwapFormUiModel(
    val selectedSrcToken: TokenBalanceUiModel? = null,
    val selectedDstToken: TokenBalanceUiModel? = null,
    val srcFiatValue: String = "0",
    val quoteDisplay: QuoteDisplay = QuoteDisplay(),
    val feeBreakdown: FeeBreakdown = FeeBreakdown(),
    val discountInfo: DiscountInfo = DiscountInfo(),
    val error: UiText? = null,
    val formError: UiText? = null,
    val isSwapDisabled: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingNextScreen: Boolean = false,
    val enableMaxAmount: Boolean = false,
    // Per-swap slippage tolerance in basis points, or null for "Auto" (#4858).
    val slippageBps: Int? = null,
)
