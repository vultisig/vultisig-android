package com.vultisig.wallet.ui.components.hero

import androidx.compose.runtime.Immutable

/**
 * Content for the dApp signing "hero" region.
 *
 * Drives the large, centered display above the transaction summary across the verify → sign → done
 * screens. The three shapes correspond to how much resolved information is available about the
 * action being signed:
 * - [Title] — function name only (decoded via 4byte), no resolved balance change. Used when
 *   Blockaid simulation failed or returned no diff. The optional [Title.caption] surfaces
 *   "Unverified function" so users see why the amount is missing.
 * - [Send] — resolved single-sided balance change, sourced from a Blockaid transfer simulation.
 * - [Swap] — resolved from-to balance change, sourced from a Blockaid swap simulation.
 *
 * Mirrors the iOS `HeroContent` enum and the vultisig-windows extension's `BlockaidTransferDisplay`
 * / `BlockaidSwapDisplay` / `EvmCalldataFallback` primitives so the three platforms render the same
 * hero from the same upstream simulation.
 */
@Immutable
sealed interface HeroContent {

    val title: String?

    @Immutable
    data class Title(val text: String, val caption: String? = null) : HeroContent {
        override val title: String
            get() = text
    }

    @Immutable data class Send(override val title: String?, val coin: HeroCoinAmount) : HeroContent

    @Immutable
    data class Swap(override val title: String?, val from: HeroCoinAmount, val to: HeroCoinAmount) :
        HeroContent
}

/**
 * Display-ready coin amount for the hero.
 *
 * `logo` carries the asset's image URL; an empty string signals "use the chain's native fallback" —
 * used for native SOL/ETH where Blockaid's per-request CDN URL would be unreliable.
 */
@Immutable data class HeroCoinAmount(val amount: String, val ticker: String, val logo: String)
