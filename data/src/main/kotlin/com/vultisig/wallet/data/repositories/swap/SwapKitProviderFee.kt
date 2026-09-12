package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.models.quotes.SwapKitFee
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger

/**
 * The provider fee a SwapKit route itemizes — its `affiliate` and `service` entries summed — in
 * base units of [coin]. This is what `SwapKitSwapPayload.swap_fee` carries to a co-signer, which
 * holds no quote and would otherwise approve a total that omits it.
 *
 * Distinct from the route's `inbound` entry, a deposit cost on the source chain that the Network
 * Fee already covers: every other platform labels this figure "Swap Fee" verbatim, so it must be
 * the provider's charge and nothing else.
 */
data class SwapKitProviderFee(val coin: Coin, val amount: BigInteger)

/**
 * Resolves the provider fee out of a route's `fees[]`, or null when the route states none it can
 * vouch for.
 *
 * Providers attribute the fee to different coins — NEAR Intents charges in the source asset,
 * Chainflip in Ethereum USDC, Flashnet in Solana USDC — so each entry's `asset` identifier is
 * matched against the coins this device can price: the pair itself, plus Ethereum USDC on a
 * Chainflip route. An entry in any other coin, a malformed or negative amount, or affiliate and
 * service entries in different coins all yield null: none of them establishes an amount to put on
 * the wire, and a wrong coin would misprice the fee by orders of magnitude on the co-signer.
 *
 * Only `asset` is matched. The entry's `chain` is the routing protocol's label (`NEAR` on an
 * Intents route) rather than the asset's chain, so keying on it would drop the fee on the routes
 * that carry one most often.
 */
fun resolveSwapKitProviderFee(
    fees: List<SwapKitFee>,
    srcToken: Coin,
    dstToken: Coin,
    subProvider: String?,
): SwapKitProviderFee? {
    val candidates = buildList {
        add(srcToken)
        add(dstToken)
        if (isChainflipProvider(subProvider)) add(Coins.Ethereum.USDC)
    }
    val candidatesByAsset =
        candidates
            .mapNotNull { coin ->
                SwapKitAssetPrefix.identifierOf(coin)?.lowercase()?.let { it to coin }
            }
            .toMap()

    var resolved: SwapKitProviderFee? = null
    for (fee in fees) {
        if (!fee.isProviderFeeEntry()) continue
        val amountText = fee.amount?.trim().orEmpty()
        if (amountText.isEmpty()) continue
        val decimal = amountText.toBigDecimalOrNull() ?: return null
        if (decimal.signum() < 0) return null
        if (decimal.signum() == 0) continue

        val coin = fee.asset?.lowercase()?.let(candidatesByAsset::get) ?: return null
        val amount = decimal.movePointRight(coin.decimal).toBigInteger()

        val current = resolved
        resolved =
            when {
                current == null -> SwapKitProviderFee(coin, amount)
                current.coin.sameAssetAs(coin) -> current.copy(amount = current.amount + amount)
                else -> return null
            }
    }
    return resolved?.takeIf { it.amount.signum() > 0 }
}

/** True for the entries that make up the provider fee: `affiliate` and `service`. */
internal fun SwapKitFee.isProviderFeeEntry(): Boolean =
    type.equals("affiliate", ignoreCase = true) || type.equals("service", ignoreCase = true)

private fun Coin.sameAssetAs(other: Coin): Boolean =
    chain == other.chain && contractAddress.equals(other.contractAddress, ignoreCase = true)

private fun isChainflipProvider(subProvider: String?): Boolean =
    subProvider.equals("CHAINFLIP", ignoreCase = true) ||
        subProvider.equals("CHAINFLIP_STREAMING", ignoreCase = true)
