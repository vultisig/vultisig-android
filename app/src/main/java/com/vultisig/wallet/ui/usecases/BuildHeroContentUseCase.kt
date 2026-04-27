package com.vultisig.wallet.ui.usecases

import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationCoin
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationInfo
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Builds the [HeroContent] for the dApp signing screens from upstream simulation + decoded function
 * name.
 *
 * Pure function (no IO, no state) so it is trivially testable. The decision tree mirrors the iOS
 * `KeysignViewModel.heroContent` and the vultisig-windows extension primitives — kept three-way
 * aligned by deliberate design.
 *
 * Truth table:
 * - simulation present → resolved [HeroContent.Send] / [HeroContent.Swap]
 * - simulation loaded but null AND function name decoded → [HeroContent.Unverified]. The decoded
 *   name is intentionally NOT surfaced in the hero — it lives in the function-signature row below
 *   so it doesn't read as the action.
 * - simulation loaded but null AND no function name → null (caller falls back to the existing
 *   native-amount hero)
 * - simulation not yet loaded → null (caller treats this as "loading")
 */
class BuildHeroContentUseCase @Inject constructor() {

    operator fun invoke(
        simulation: BlockaidSimulationInfo?,
        decodedFunctionName: String?,
        didLoadSimulation: Boolean,
    ): HeroContent? {
        simulation?.let {
            return when (it) {
                is BlockaidSimulationInfo.Transfer ->
                    HeroContent.Send(
                        title = decodedFunctionName,
                        coin = it.fromCoin.toHeroAmount(it.fromAmountText()),
                    )

                is BlockaidSimulationInfo.Swap ->
                    HeroContent.Swap(
                        title = decodedFunctionName,
                        from = it.fromCoin.toHeroAmount(it.fromAmountText()),
                        to = it.toCoin.toHeroAmount(it.toAmountText()),
                    )
            }
        }

        if (didLoadSimulation && decodedFunctionName != null) return HeroContent.Unverified

        return null
    }

    private fun BlockaidSimulationCoin.toHeroAmount(formatted: String): HeroCoinAmount =
        HeroCoinAmount(amount = formatted, ticker = ticker, logo = logo)

    private fun BlockaidSimulationInfo.fromAmountText(): String =
        formatAmount(fromAmount, fromCoin.decimals)

    private fun BlockaidSimulationInfo.Swap.toAmountText(): String =
        formatAmount(toAmount, toCoin.decimals)

    /**
     * Formats a wire-side raw amount into a plain decimal string.
     *
     * Uses [BigDecimal.movePointLeft] + [BigDecimal.setScale] rather than [BigDecimal.divide] with
     * a precision-bounded `MathContext`: the latter rounds significant digits, which can clip
     * integer digits for sentinel values like a `MAX_UINT256` approval. Scaling the fractional part
     * instead preserves all integer digits and only rounds beyond [MAX_FRACTION_SCALE] decimal
     * places.
     */
    private fun formatAmount(rawValue: BigInteger, decimals: Int): String =
        rawValue
            .toBigDecimal()
            .movePointLeft(decimals)
            .setScale(MAX_FRACTION_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private companion object {
        // Eighteen fractional digits matches Ether wei-precision. Beyond that
        // the hero has no use for sub-wei detail — Blockaid does not return
        // values with finer granularity, and the row would not have room for
        // them anyway.
        private const val MAX_FRACTION_SCALE = 18
    }
}
