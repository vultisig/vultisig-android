package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.ui.navigation.Route

/**
 * The pair a failed or refunded market swap is tried again on (#5918): the swap form reopens with
 * these two tokens selected and the user takes it from there — amount, route and review are all
 * entered afresh, and nothing from the failed swap is reused.
 */
data class SwapRetry(val srcToken: Coin, val dstToken: Coin) {
    internal fun toRoute(vaultId: String): Route.Swap =
        Route.Swap(
            vaultId = vaultId,
            chainId = srcToken.chain.id,
            srcTokenId = srcToken.id,
            dstTokenId = dstToken.id,
        )
}

/**
 * Resolves a history row to the pair it can offer against the vault's current [coins], or null when
 * the button must stay hidden: a limit order (out of scope), or a side whose token the vault no
 * longer holds — or holds twice, where picking one would be guessing which asset to sell.
 */
internal fun SwapTransactionHistoryData.toSwapRetry(coins: List<Coin>): SwapRetry? {
    if (isLimitOrder) return null
    val src = coins.singleOrNull { it.matches(fromChain, fromToken, fromContractAddress) }
    val dst = coins.singleOrNull { it.matches(toChain, toToken, toContractAddress) }
    if (src == null || dst == null) return null
    return SwapRetry(srcToken = src, dstToken = dst)
}

/**
 * A row recorded before it carried a contract address, and a native token on any row, both leave
 * [contractAddress] empty, so an empty one matches on chain and ticker alone and the caller's
 * uniqueness check decides. A non-empty one is compared case-folded on EVM, where the same contract
 * may be held checksummed and re-added lowercase, and exactly everywhere else, where case is part
 * of the address.
 */
private fun Coin.matches(chainId: String, ticker: String, contractAddress: String): Boolean =
    chain.id == chainId &&
        this.ticker == ticker &&
        (contractAddress.isEmpty() ||
            if (chain.standard == TokenStandard.EVM) {
                this.contractAddress.equals(contractAddress, ignoreCase = true)
            } else {
                this.contractAddress == contractAddress
            })
