package com.vultisig.wallet.ui.components.hero

import androidx.compose.runtime.Immutable

/**
 * Content for the dApp signing "hero" region.
 *
 * Drives the large, centered display above the transaction summary across the verify → sign → done
 * screens. The four shapes correspond to how much resolved information is available about the
 * action being signed:
 * - [Title] — bare function name fallback. Used by the done screens before the simulation has
 *   propagated. Carries no warning copy because at that point the signature is already on chain.
 * - [Unverified] — explicit "Unverified function" hero (warning glyph + localized title +
 *   review-details subtitle). Emitted by the use case when Blockaid simulation has loaded but
 *   returned no balance change. The localized strings are resolved at the composable boundary so
 *   the data type stays Android-resource-free.
 * - [Send] — resolved single-sided balance change, sourced from a Blockaid transfer simulation.
 * - [Swap] — resolved from-to balance change, sourced from a Blockaid swap simulation.
 *
 * Mirrors the iOS `HeroContent` enum and the vultisig-windows extension's `BlockaidTransferDisplay`
 * / `BlockaidSwapDisplay` / `EvmCalldataFallback` primitives so the three platforms render the same
 * hero from the same upstream simulation.
 */
@Immutable
sealed interface HeroContent {

    @Immutable data class Title(val title: String) : HeroContent

    @Immutable data object Unverified : HeroContent

    @Immutable data class Send(val title: String?, val coin: HeroCoinAmount) : HeroContent

    @Immutable
    data class Swap(val title: String?, val from: HeroCoinAmount, val to: HeroCoinAmount) :
        HeroContent
}

/**
 * Display-ready coin amount for the hero.
 *
 * `logo` carries the asset's image URL; an empty string signals "use the chain's native fallback" —
 * used for native SOL/ETH where Blockaid's per-request CDN URL would be unreliable.
 */
@Immutable data class HeroCoinAmount(val amount: String, val ticker: String, val logo: String)
