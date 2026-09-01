package com.vultisig.wallet.ui.components.hero

import androidx.compose.runtime.Immutable

/**
 * Content for the dApp signing "hero" region.
 *
 * Drives the large, centered display above the transaction summary across the verify → sign → done
 * screens. The shapes correspond to how much resolved information is available about the action
 * being signed:
 * - [Title] — bare function name fallback. Used by the done screens before the simulation has
 *   propagated. Carries no warning copy because at that point the signature is already on chain.
 * - [Unverified] — explicit "Unverified function" hero (warning glyph + localized title +
 *   review-details subtitle). Emitted by the use case when Blockaid simulation has loaded but
 *   returned no balance change. The localized strings are resolved at the composable boundary so
 *   the data type stays Android-resource-free.
 * - [Send] — resolved single-sided balance change, sourced from a Blockaid transfer simulation or
 *   from a signed amount the transaction decoder read.
 * - [Swap] — resolved from-to balance change, sourced from a Blockaid swap simulation.
 * - [Projected] — a settlement estimate paired with the scope the transaction commits to. Distinct
 *   from [Send] so an estimate can never look committed.
 *
 * Mirrors the iOS `HeroContent` enum and the vultisig-windows extension's `BlockaidTransferDisplay`
 * / `BlockaidSwapDisplay` / `EvmCalldataFallback` primitives so the three platforms render the same
 * hero from the same upstream simulation.
 */
@Immutable
sealed interface HeroContent {

    /** The verb, when this shape carries one. */
    val title: String?

    @Immutable data class Title(override val title: String) : HeroContent

    @Immutable
    data object Unverified : HeroContent {
        override val title: String? = null
    }

    @Immutable data class Send(override val title: String?, val coin: HeroCoinAmount) : HeroContent

    @Immutable
    data class Swap(override val title: String?, val from: HeroCoinAmount, val to: HeroCoinAmount) :
        HeroContent

    /**
     * A settlement estimate paired with the scope the transaction commits to. Callers must
     * separately disclose any signed carrier amount this hero replaces.
     */
    @Immutable
    data class Projected(
        override val title: String?,
        val estimate: HeroCoinAmount?,
        val scope: String,
    ) : HeroContent
}

/** Replaces only the verb, preserving richer figures. A null [title] is inert. */
internal fun HeroContent.retitled(title: String?): HeroContent =
    if (title == null) this
    else
        when (this) {
            is HeroContent.Title -> HeroContent.Title(title)
            HeroContent.Unverified -> this
            is HeroContent.Send -> copy(title = title)
            is HeroContent.Swap -> copy(title = title)
            is HeroContent.Projected -> copy(title = title)
        }

/**
 * Display-ready coin amount for the hero.
 *
 * `logo` carries the asset's image URL; an empty string signals "use the chain's native fallback" —
 * used for native SOL/ETH where Blockaid's per-request CDN URL would be unreliable.
 *
 * `fiatValue` is the pre-formatted fiat worth of `amount` (e.g. `$12.34`), rendered as a sub-line
 * under the amount. Null when no price was resolvable, or for callers that intentionally omit fiat
 * — a Blockaid simulation prices nothing, so only decoder-built amounts populate it.
 */
@Immutable
data class HeroCoinAmount(
    val amount: String,
    val ticker: String,
    val logo: String,
    val fiatValue: String? = null,
)
