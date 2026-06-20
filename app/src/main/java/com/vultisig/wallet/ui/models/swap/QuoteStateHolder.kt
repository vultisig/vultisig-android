package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable swap-quote state confined to the main thread, and the single owner of the quote-coupled
 * fee flow.
 *
 * Every read/write happens from Main.immediate-dispatched code (the quote pipeline, the flip
 * gesture, and the reset paths), so the plain `var`s need no synchronization. Grouping them here
 * keeps that threading invariant explicit in one place, and lets [reset] clear the whole quote unit
 * from one place instead of poking scattered fields.
 */
internal class QuoteStateHolder {
    var quote: SwapQuote? = null
    var provider: SwapProvider? = null
    var preFlipState: PreFlipState? = null

    // Latest resolved swap fee. collectTotalFee() combines it with the gas fee; filterNotNull()
    // short-circuits while it is null so a later gas update can't write a (newGas + staleSwap)
    // total back into state during a reset or flip.
    val swapFeeFiat = MutableStateFlow<FiatValue?>(null)

    // Whether the resolved best quote honors the EVM gas-limit override — only the EVM-aggregator
    // route (SwapQuote.OneInch) applies it at build time; THORChain/Maya ignore it. null until a
    // quote resolves. Drives the Gas Limit row's applicability so it isn't shown enabled for a
    // route
    // that would silently drop the value (#4858 review).
    val honorsGasLimitOverride = MutableStateFlow<Boolean?>(null)

    /**
     * Clears the quote and its swap fee on reset / flip / error. [preFlipState] is owned by the
     * flip gesture and intentionally not cleared here. Network-fee state is gas-coupled (owned by
     * calculateGas), not quote-coupled, so it is deliberately left untouched.
     */
    fun reset() {
        quote = null
        provider = null
        swapFeeFiat.value = null
        honorsGasLimitOverride.value = null
    }
}
