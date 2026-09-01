package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.swap.SwapMode
import com.vultisig.wallet.ui.utils.UiText
import java.time.Instant

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
    // True when the affiliate fee is baked into the quoted rate (1inch): the Swap Fee row shows
    // "included in quoted rate" instead of a fiat amount, and the fee adds nothing to the total
    // (#5358).
    val swapFeeIncludedInRate: Boolean = false,
    // Signed price-impact percentage (e.g. "-1.33%") and its tier, or null when the provider does
    // not report price impact (1inch/Kyber/LiFi/Jupiter). Drives the Price Impact row (iOS parity).
    val priceImpactPercent: String? = null,
    val priceImpactLevel: PriceImpactLevel? = null,
)

/**
 * One row of the Select-route picker in the Advanced swap sheet: a fetched provider quote the user
 * can pick over the automatic winner. Rows are ordered best→worst by net destination output, with
 * the active route pinned to the top.
 *
 * @property feeText The provider/swap fee in fiat, or null when the fee is baked into the quoted
 *   rate (1inch) and there is no separate amount to show.
 * @property etaText Estimated completion time ("~600s"); only THORChain/Maya expose an estimate, so
 *   null hides the segment for aggregator routes.
 * @property outputText Approximate destination output, e.g. "~21.83561311 RUNE".
 */
internal data class SwapRouteUiModel(
    val provider: SwapProvider,
    val name: UiText,
    val logo: ImageModel?,
    val feeText: String?,
    val etaText: UiText?,
    val outputText: String,
    val outputFiatText: String,
    val isSelected: Boolean,
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
    // Per-swap EVM gas-limit override (units), or null for "Auto" (#4858).
    val gasLimitOverride: Long? = null,
    // Whether a custom gas limit applies to the current source chain (EVM only); the Gas Limit
    // row is disabled otherwise.
    val isGasLimitApplicable: Boolean = false,
    // Optional external recipient address; null/blank = off (swap routes to the vault) (#4858).
    val externalRecipient: String? = null,
    // Set when [externalRecipient] is not a valid address for the destination chain. Surfaced
    // inline in the recipient sheet and blocks the swap so funds can't go to a malformed address.
    val externalRecipientError: UiText? = null,
    // Fetched routes for the Select-route picker, active route first then best→worst by output.
    // Empty when fewer than two routes exist — the row is disabled and there is nothing to pick.
    val routeOptions: List<SwapRouteUiModel> = emptyList(),
    // True while the active route is a manual user pick rather than the automatic winner; the
    // Select route row then shows the provider name instead of "Auto". Reset on every quote
    // refresh, which re-defaults the route to the best quote.
    val isRouteManuallySelected: Boolean = false,
    // Whether the Advanced swap sheet is open. Opened only after the VULT Silver-tier gate passes;
    // a below-tier vault sees [advancedSettingsGate] instead (#4858).
    val showAdvancedSettings: Boolean = false,
    // Non-null when the vault is below the Silver tier required for advanced settings — drives the
    // tier-locked upsell sheet shown in place of the advanced sheet (#4858).
    val advancedSettingsGate: VultTierGateUiModel? = null,
    // Market vs Limit tab selection, owned by the ViewModel so the limit form and its gating can
    // react to it.
    val swapMode: SwapMode = SwapMode.Market,
    // Whether the Limit tab is usable: the remote feature flag is on AND the selected pair is
    // THORChain-routable. When false the tab shows the coming-soon placeholder.
    val isLimitTabEnabled: Boolean = false,
    // Limit-order form state, non-null only while the Limit tab is active with a routable pair.
    val limitOrder: LimitOrderUiModel? = null,
)

/**
 * Data for the tier-locked upsell sheet shown when a below-Silver vault taps Advanced Settings: the
 * vault's formatted $VULT balance and whether it falls short of the required threshold (#4858).
 */
internal data class VultTierGateUiModel(
    val balanceText: String,
    val thresholdText: String,
    val isBelowThreshold: Boolean,
)
