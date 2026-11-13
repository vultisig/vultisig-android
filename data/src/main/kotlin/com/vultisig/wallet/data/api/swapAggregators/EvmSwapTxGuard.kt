package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
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
 * - Fynd settles through one TychoRouterV3 per chain, the Designated Router its license requires
 *   (Propeller Heads Router Registry). Its JIT router is never accepted: Vultisig's key isn't
 *   enrolled for it, so a quote addressed there didn't come from our integration.
 * - A 1inch / Kyber / Fynd swap (or a provider-less one aimed at their routers) can't send more native
 *   value than the quoted amount, and sends none from an ERC-20 source. LI.FI and SwapKit are
 *   exempt: bridge routes add native messaging fees on top of the quoted amount, so `tx.value`
 *   can legitimately exceed it. LI.FI stays bound by its router pin.
 *
 * Router addresses mirror vultisig-sdk's `knownAggregatorRouters.ts`, which were confirmed against
 * each provider's live API and deployment registry.
 */
internal object EvmSwapTxGuard {

    fun check(swapPayload: EVMSwapPayloadJson) {
        // SigningHelper.getKeysignMessages has refused a fromCoin other than the keysign coin, so
        // this is the chain the transaction is signed on.
        val chain = swapPayload.fromCoin.chain
        require(chain.standard == TokenStandard.EVM) { "EVM swap signed for ${chain.raw}" }
        val tx = swapPayload.quote.tx
        val value = tx.nativeValue()

        val rawProvider = swapPayload.provider.trim()
        val provider = swapProviderFromWireId(rawProvider)
        if (provider == SwapProvider.SWAPKIT) {
            // Called for its throws only: a deposit-shaped tx must be exactly the deposit.
            swapPayload.swapKitDepositRecipient(chain)
        }
        // Keyed on the destination, not the label: a provider-less payload may target the router too.
        if (provider == SwapProvider.FYND || tx.to.equals(fyndRouter(chain), ignoreCase = true)) {
            checkFyndCall(swapPayload)
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
                SwapProvider.KYBER,
                SwapProvider.FYND -> true
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

    /**
     * Fynd's router call names its own amounts, tokens and receiver; they must be the swap this
     * payload shows, paid to the signing vault, with a minimum no higher than the quoted output.
     */
    private fun checkFyndCall(swapPayload: EVMSwapPayloadJson) {
        val call = FyndRouterCalldata.decode(swapPayload.quote.tx.data)
        val from = swapPayload.fromCoin
        val to = swapPayload.toCoin
        require(call.amountIn == swapPayload.fromAmount) {
            "Fynd swap sells ${call.amountIn}, payload shows ${swapPayload.fromAmount}"
        }
        require(call.tokenIn.equals(from.fyndTokenAddress(), ignoreCase = true)) {
            "Fynd swap sells ${call.tokenIn}, not ${from.ticker}"
        }
        require(call.tokenOut.equals(to.fyndTokenAddress(), ignoreCase = true)) {
            "Fynd swap buys ${call.tokenOut}, not ${to.ticker}"
        }
        require(call.receiver.equals(from.address, ignoreCase = true)) {
            "Fynd swap pays ${call.receiver}, not the signing vault"
        }
        val quoted = requireNotNull(swapPayload.quote.dstAmount.toBigIntegerOrNull()) {
            "Fynd swap quote dstAmount '${swapPayload.quote.dstAmount}' is not an integer"
        }
        require(call.minAmountOut.signum() > 0 && call.minAmountOut <= quoted) {
            "Fynd swap minimum ${call.minAmountOut} outside (0, $quoted]"
        }
        require(call.maxClientContribution.signum() == 0) {
            "Fynd swap lets the fee receiver subsidise slippage"
        }
        // The payload carries no slippage, so the minimum can't be floored here (as for 1inch and
        // Kyber); the fee, though, can only ever be Vultisig's, at no more than the list rate.
        if (call.clientFeeUnits.signum() != 0) {
            val receiver = requireNotNull(FyndApiImpl.FEE_RECEIVER) {
                "Fynd swap charges a client fee but no Vultisig fee receiver is configured"
            }
            require(call.clientFeeReceiver.equals(receiver, ignoreCase = true)) {
                "Fynd swap pays its client fee to ${call.clientFeeReceiver}, not Vultisig"
            }
            require(call.clientFeeUnits <= FYND_MAX_CLIENT_FEE_UNITS) {
                "Fynd swap client fee ${call.clientFeeUnits} is above the list rate"
            }
        }
    }

    private fun Coin.fyndTokenAddress(): String =
        if (isNativeToken) "0x0000000000000000000000000000000000000000" else contractAddress

    private fun exactValueRouters(chain: Chain): Set<String> =
        routersFor(SwapProvider.ONEINCH, chain) +
            routersFor(SwapProvider.KYBER, chain) +
            routersFor(SwapProvider.FYND, chain)

    /** Fynd's Designated Router on [chain] (lowercase), or null where Fynd isn't integrated. */
    fun fyndRouter(chain: Chain): String? =
        when (chain) {
            Chain.Ethereum -> FYND_ROUTER_ETHEREUM
            Chain.Base -> FYND_ROUTER_BASE
            Chain.Arbitrum -> FYND_ROUTER_ARBITRUM
            else -> null
        }

    private fun routersFor(provider: SwapProvider, chain: Chain): Set<String> =
        when (provider) {
            SwapProvider.ONEINCH ->
                when (chain) {
                    Chain.ZkSync -> setOf(ONE_INCH_V6_ZKSYNC_ROUTER)
                    Chain.Robinhood -> setOf(ONE_INCH_V6_ROBINHOOD_ROUTER)
                    else -> setOf(ONE_INCH_V6_STANDARD_ROUTER, ONE_INCH_V5_ROUTER)
                }
            SwapProvider.KYBER -> setOf(KYBER_ROUTER)
            SwapProvider.FYND -> setOfNotNull(fyndRouter(chain))
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
        listOf(SwapProvider.ONEINCH, SwapProvider.KYBER, SwapProvider.LIFI, SwapProvider.FYND)

    private const val ONE_INCH_V5_ROUTER = "0x1111111254eeb25477b68fb85ed929f73a960582"
    private const val ONE_INCH_V6_STANDARD_ROUTER = "0x111111125421ca6dc452d289314280a0f8842a65"
    private const val ONE_INCH_V6_ZKSYNC_ROUTER = "0x6fd4383cb451173d5f9304f041c7bcbf27d561ff"
    private const val ONE_INCH_V6_ROBINHOOD_ROUTER = "0x5a705de8982235a7fa45bb83dcacf03a211389c7"
    private const val KYBER_ROUTER = "0x6131b5fae19ea4f9d964eac0408e4408b66337b5"
    private const val LIFI_DIAMOND = "0x1231deb6f5749ef6ce6943a275a1d3e7486f4eae"
    private const val LIFI_DIAMOND_HYPERLIQUID = "0x0a0758d937d1059c356d4714e57f5df0239bce1a"
    private const val LIFI_DIAMOND_ROBINHOOD = "0xb477751b76cf82d00a686a1232f5fcd772414af3"
    private const val LIFI_DIAMOND_ZKSYNC = "0x341e94069f53234fe6dabef707ad424830525715"
    // 50 bps list rate grossed up for Fynd's 20% share (63 bps), in router fee units.
    private val FYND_MAX_CLIENT_FEE_UNITS = BigInteger.valueOf(630_000)
    // TychoRouterV3, active since 2026-09-01 in propeller-heads/tycho deployment_registry.json.
    private const val FYND_ROUTER_ETHEREUM = "0x1644d2477f809cc2c71bccfd6dc9497e3f83210d"
    private const val FYND_ROUTER_BASE = "0xaba5b53b03eafad1c5fc8bd5fc765fc85bb3de67"
    private const val FYND_ROUTER_ARBITRUM = "0x924f147c50ea59f5180a26031a8b65b2aa1e81cd"
}

/**
 * The native value this swap tx sends; how every EVM swap check reads `tx.value`.
 *
 * @throws IllegalArgumentException when `tx.value` is not a non-negative integer.
 */
internal fun OneInchSwapTxJson.nativeValue(): BigInteger =
    requireNotNull(value.toBigIntegerOrNull()?.takeIf { it >= BigInteger.ZERO }) {
        "EVM swap tx.value '$value' is not a non-negative integer"
    }
