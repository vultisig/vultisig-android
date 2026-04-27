package com.vultisig.wallet.ui.usecases

import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationCoin
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationInfo
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import java.math.BigDecimal
import java.math.MathContext
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
 * - simulation loaded but null AND function name decoded → [HeroContent.Title] with the localized
 *   "Unverified function" title + review-details subtitle. The decoded name is intentionally NOT
 *   surfaced in the hero — it lives in the function-signature row below so it doesn't read as the
 *   action.
 * - simulation loaded but null AND no function name → null (caller falls back to the existing
 *   native-amount hero)
 * - simulation not yet loaded → null (caller treats this as "loading")
 */
class BuildHeroContentUseCase @Inject constructor() {

    operator fun invoke(
        simulation: BlockaidSimulationInfo?,
        decodedFunctionName: String?,
        didLoadSimulation: Boolean,
        unverifiedFunctionTitle: String,
        unverifiedFunctionSubtitle: String,
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

        if (didLoadSimulation && decodedFunctionName != null) {
            return HeroContent.Title(
                text = unverifiedFunctionTitle,
                caption = unverifiedFunctionSubtitle,
            )
        }

        return null
    }

    private fun BlockaidSimulationCoin.toHeroAmount(formatted: String): HeroCoinAmount =
        HeroCoinAmount(amount = formatted, ticker = ticker, logo = logo)

    private fun BlockaidSimulationInfo.fromAmountText(): String =
        formatAmount(fromAmount.toBigDecimal(), fromCoin.decimals)

    private fun BlockaidSimulationInfo.Swap.toAmountText(): String =
        formatAmount(toAmount.toBigDecimal(), toCoin.decimals)

    private fun formatAmount(rawValue: BigDecimal, decimals: Int): String {
        val divisor = BigDecimal.TEN.pow(decimals)
        val human = rawValue.divide(divisor, MathContext(MAX_PRECISION, RoundingMode.HALF_UP))
        val stripped = human.stripTrailingZeros().toPlainString()
        // BigDecimal renders integer-valued numbers without decimals once
        // trailing zeros are stripped, which is what the hero wants.
        return stripped
    }

    private companion object {
        // Up to 18 significant digits — enough for ETH wei-precision while
        // staying well clear of `Decimal`'s overflow boundary on extreme
        // values (Blockaid sometimes returns 1e30 sentinels for unlimited
        // approvals).
        private const val MAX_PRECISION = 30
    }
}
