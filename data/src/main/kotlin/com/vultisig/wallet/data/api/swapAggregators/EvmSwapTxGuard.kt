package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.swapProviderFromWireId
import java.math.BigInteger

/**
 * Checks an EVM aggregator swap before it's signed. The co-signer rebuilds the transaction from the
 * relayed payload and its Verify screen only shows the quoted tokens and amounts, so `tx.to` and
 * `tx.value` must be ones that quote implies rather than whatever the provider response (or a
 * compromised initiator) put there.
 * - `tx.to` must be the provider's router on that chain. SwapKit is exempt: its entry contract is
 *   chosen per route, so there's no fixed address to pin. A SwapKit `transfer` call, or a SwapKit
 *   tx addressed to the sold token (an ERC-20 deposit), must instead be exactly
 *   `transfer(recipient, fromAmount)` on the sold token with no native value
 *   ([swapKitDepositRecipient]).
 * - A 1inch / Kyber swap (or a provider-less one aimed at their routers) can't send more native
 *   value than the quoted amount, and sends none from an ERC-20 source. LI.FI and SwapKit are
 *   exempt: bridge routes add native messaging fees on top of the quoted amount, so `tx.value`
 *   can legitimately exceed it. LI.FI stays bound by its router pin.
 *
 * Router addresses mirror vultisig-sdk's `knownAggregatorRouters.ts`, which were confirmed against
 * each provider's live API and deployment registry.
 */
internal object EvmSwapTxGuard {

    /**
     * @param signingCoin the keysign coin: the transaction is signed for its chain, so the checks
     *   run on that chain, as vultisig-sdk's `getKeysignSwapKitDepositRecipient` and iOS
     *   `EVMSwapTxGuard.check` key them.
     */
    fun check(swapPayload: EVMSwapPayloadJson, signingCoin: Coin) {
        val chain = signingCoin.chain
        require(chain.standard == TokenStandard.EVM) { "EVM swap signed for ${chain.raw}" }
        val tx = swapPayload.quote.tx
        val value =
            tx.value.toBigIntegerOrNull()?.takeIf { it >= BigInteger.ZERO }
                ?: error("EVM swap tx.value '${tx.value}' is not a non-negative integer")

        val rawProvider = swapPayload.provider.trim()
        val provider = swapProviderFromWireId(rawProvider)
        if (provider == SwapProvider.SWAPKIT) {
            require(swapPayload.fromCoin.chain == chain) {
                "SwapKit swap sells a ${swapPayload.fromCoin.chain.raw} coin but signs on ${chain.raw}"
            }
            swapPayload.swapKitDepositRecipient(chain)
        }
        val routers =
            when {
                provider == SwapProvider.SWAPKIT -> null
                provider != null -> routersFor(provider, chain)
                rawProvider.isEmpty() -> ENFORCED_PROVIDERS.flatMap { routersFor(it, chain) }.toSet()
                else -> error("EVM swap from unrecognized provider '$rawProvider'")
            }
        if (routers != null) {
            require(tx.to.lowercase() in routers) {
                "EVM swap router ${tx.to} is not a known ${rawProvider.ifEmpty { "aggregator" }} " +
                    "router on ${chain.raw}"
            }
        }

        val boundsValue =
            when (provider) {
                SwapProvider.ONEINCH,
                SwapProvider.KYBER -> true
                null -> tx.to.lowercase() in exactValueRouters(chain)
                else -> false
            }
        if (!boundsValue) return
        if (swapPayload.fromCoin.isNativeToken) {
            require(value <= swapPayload.fromAmount) {
                "EVM swap sends $value native units, more than the quoted ${swapPayload.fromAmount}"
            }
        } else {
            require(value.signum() == 0) {
                "EVM swap from an ERC-20 source must not send native value, got $value"
            }
        }
    }

    private fun exactValueRouters(chain: Chain): Set<String> =
        routersFor(SwapProvider.ONEINCH, chain) + routersFor(SwapProvider.KYBER, chain)

    private fun routersFor(provider: SwapProvider, chain: Chain): Set<String> =
        when (provider) {
            SwapProvider.ONEINCH ->
                when (chain) {
                    Chain.ZkSync -> setOf(ONE_INCH_V6_ZKSYNC_ROUTER)
                    Chain.Robinhood -> setOf(ONE_INCH_V6_ROBINHOOD_ROUTER)
                    else -> setOf(ONE_INCH_V6_STANDARD_ROUTER, ONE_INCH_V5_ROUTER)
                }
            SwapProvider.KYBER -> setOf(KYBER_ROUTER)
            SwapProvider.LIFI ->
                when (chain) {
                    Chain.Hyperliquid -> setOf(LIFI_DIAMOND_HYPERLIQUID)
                    Chain.Robinhood -> setOf(LIFI_DIAMOND_ROBINHOOD)
                    Chain.ZkSync -> setOf(LIFI_DIAMOND_ZKSYNC)
                    else -> setOf(LIFI_DIAMOND)
                }
            else -> emptySet()
        }

    private val ENFORCED_PROVIDERS =
        listOf(SwapProvider.ONEINCH, SwapProvider.KYBER, SwapProvider.LIFI)

    private const val ONE_INCH_V5_ROUTER = "0x1111111254eeb25477b68fb85ed929f73a960582"
    private const val ONE_INCH_V6_STANDARD_ROUTER = "0x111111125421ca6dc452d289314280a0f8842a65"
    private const val ONE_INCH_V6_ZKSYNC_ROUTER = "0x6fd4383cb451173d5f9304f041c7bcbf27d561ff"
    private const val ONE_INCH_V6_ROBINHOOD_ROUTER = "0x5a705de8982235a7fa45bb83dcacf03a211389c7"
    private const val KYBER_ROUTER = "0x6131b5fae19ea4f9d964eac0408e4408b66337b5"
    private const val LIFI_DIAMOND = "0x1231deb6f5749ef6ce6943a275a1d3e7486f4eae"
    private const val LIFI_DIAMOND_HYPERLIQUID = "0x0a0758d937d1059c356d4714e57f5df0239bce1a"
    private const val LIFI_DIAMOND_ROBINHOOD = "0xb477751b76cf82d00a686a1232f5fcd772414af3"
    private const val LIFI_DIAMOND_ZKSYNC = "0x341e94069f53234fe6dabef707ad424830525715"
}
