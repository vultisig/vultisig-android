package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.ui.navigation.Route

/**
 * What goes back into the swap form when a failed or refunded market swap is tried again (#5918):
 * the pair and the amount, and nothing else. The old quote and keysign payload are never reused —
 * the form re-quotes the best route and Verify shows the new rate before anything is signed.
 */
data class SwapRetry(
    val srcToken: Coin,
    val dstToken: Coin,
    /** Plain decimal, e.g. `12.5`, exactly as the form's amount field takes it. */
    val srcAmount: String,
) {
    internal fun toRoute(vaultId: String): Route.Swap =
        Route.Swap(
            vaultId = vaultId,
            chainId = srcToken.chain.id,
            srcTokenId = srcToken.id,
            dstTokenId = dstToken.id,
            srcAmount = srcAmount,
            verifyOnQuote = true,
        )
}

/**
 * Resolves a history row to the retry it can offer against the vault's current [coins], or null
 * when the button must stay hidden: a limit order (its terms live in a memo the form does not
 * restore), a legacy row without a machine-readable amount, or a side whose token the vault no
 * longer holds — or holds twice, where picking one would be guessing which asset to sell.
 */
internal fun SwapTransactionHistoryData.toSwapRetry(coins: List<Coin>): SwapRetry? {
    if (isLimitOrder) return null
    val amount = fromAmountDecimal.takeIf { it.isNotEmpty() } ?: return null
    val src = coins.singleOrNull { it.matches(fromChain, fromToken, fromContractAddress) }
    val dst = coins.singleOrNull { it.matches(toChain, toToken, toContractAddress) }
    if (src == null || dst == null) return null
    return SwapRetry(srcToken = src, dstToken = dst, srcAmount = amount)
}

private fun Coin.matches(chainId: String, ticker: String, contractAddress: String): Boolean =
    chain.id == chainId &&
        this.ticker == ticker &&
        this.contractAddress.equals(contractAddress, ignoreCase = true)
