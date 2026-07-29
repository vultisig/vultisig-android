package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import com.vultisig.wallet.data.swap.limit.LimitSwapMemo
import com.vultisig.wallet.data.swap.limit.buildLimitSwapMemoForCoins
import com.vultisig.wallet.data.swap.limit.fromThorchainFixedPoint
import com.vultisig.wallet.data.swap.limit.thorchainMemoAssetChainPrefix
import com.vultisig.wallet.data.swap.limit.toThorchainFixedPoint
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Assembles the [RegularSwapTransaction] for a THORChain limit order.
 *
 * Mirrors the Vultisig SDK's `buildLimitSwapKeysignPayload`, reusing the market swap's keysign
 * pipeline (`SwapPayload.ThorChain` + memo → verify → keysign). The only substitution is a
 * locally-built `=<` memo in place of the server quote's memo; the route itself is an ordinary
 * THORChain swap, so the inbound vault, the ERC20 router, and the affiliate / outbound fee
 * breakdown are all resolved exactly as the market path resolves them.
 *
 * How the memo reaches the chain depends on the source asset, exactly like the market path:
 * - **native RUNE** → `MsgDeposit` on THORChain (no inbound vault; the deposit target is the
 *   signer's own address as a placeholder — the Cosmos signer keys off the deposit shape, not the
 *   address).
 * - **native gas asset** (BTC/ETH/…) → a transfer to the Asgard inbound vault with the memo in
 *   `data`/`OP_RETURN`.
 * - **ERC20** → the router's `depositWithExpiry` call (needs `approve(router, amount)` first).
 *
 * Fund-safety gate: the `EnableAdvSwapQueue` mimir is re-checked here at placement time — a `=<`
 * order placed while the queue is disabled can execute as an unprotected market swap. The sign-time
 * re-check (the mimir can flip while the user sits on the confirmation screen) lives in
 * [SwapInboundHaltPreflight], keyed off the `=<` memo.
 */
internal class BuildLimitSwapTransactionUseCase
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val thorMimirRepository: ThorMimirRepository,
    private val swapGasCalculator: SwapGasCalculator,
    private val allowanceRepository: AllowanceRepository,
    private val swapQuoteRepository: SwapQuoteRepository,
) {

    data class Params(
        val vaultId: String,
        val srcToken: Coin,
        val dstToken: Coin,
        val srcAddress: String,
        val srcTokenValue: TokenValue,
        /** Buy-asset units per 1 sell-asset unit (the memo's LIM math input). */
        val targetPrice: BigDecimal,
        val expiryHours: Int,
        /**
         * Payout address on the target chain — the vault's own address (external recipients TBD).
         */
        val destinationAddress: String,
        val gasFee: TokenValue,
        val gasFeeFiatValue: FiatValue,
        val estimatedNetworkFeeTokenValue: TokenValue?,
        val estimatedNetworkFeeFiatValue: FiatValue?,
        val now: Long = System.currentTimeMillis(),
    )

    suspend fun build(params: Params): RegularSwapTransaction {
        val (srcToken, dstToken) = params.srcToken to params.dstToken

        if (!thorMimirRepository.isAdvancedSwapQueueEnabled()) {
            error(
                "THORChain's advanced swap queue is disabled; a limit order placed now could " +
                    "execute as an unprotected market swap"
            )
        }

        val memo =
            buildLimitSwapMemoForCoins(
                fromCoin = srcToken,
                toCoin = dstToken,
                amount = params.srcTokenValue.value,
                targetPrice = params.targetPrice,
                expiryHours = params.expiryHours,
                destinationAddress = params.destinationAddress,
            )
        LimitSwapMemo.assertLimitSwapMemo(memo)

        val isRuneDeposit = srcToken.chain == Chain.ThorChain && srcToken.isNativeToken
        val isEvmToken = !srcToken.isNativeToken && srcToken.chain.standard == TokenStandard.EVM

        val inbound =
            if (isRuneDeposit) {
                null
            } else {
                val prefix =
                    thorchainMemoAssetChainPrefix[srcToken.chain]
                        ?: error("${srcToken.chain} is not routable through THORChain")
                // Reject halted or trading-paused inbounds here, matching the sign-time preflight,
                // so a placement can't reach keysign for a route that is already unavailable.
                thorChainApi.getTHORChainInboundAddresses().firstOrNull {
                    it.chain.trim().uppercase() == prefix &&
                        !it.halted &&
                        !it.globalTradingPaused &&
                        !it.chainTradingPaused
                } ?: error("No live THORChain inbound for ${srcToken.chain}")
            }

        // A limit order settles on the ordinary THORChain swap route, so its route details come
        // from the ordinary swap quote — the ERC20 router here, and the affiliate / outbound fee
        // breakdown below. One quote serves both. The cosigner fetches this same quote when it
        // rebuilds the confirmation screen from the payload, which is what keeps the two devices'
        // fee rows in agreement instead of each showing its own number.
        val quote = fetchSwapQuote(params)

        // An EVM ERC20 must deposit through the router's depositWithExpiry (which carries the
        // memo). With no router it would fall back to a plain transfer that drops the memo and
        // strands the tokens on the inbound vault — never a protected limit order — so fail closed
        // instead.
        val router =
            if (isEvmToken) {
                quote?.router?.takeIf { it.isNotBlank() }
                    ?: error(
                        "THORChain has no router for ${srcToken.chain}; cannot place an ERC20 " +
                            "limit order"
                    )
            } else {
                null
            }
        val vaultAddress = inbound?.address ?: params.srcAddress
        val isRouterDeposit = isEvmToken
        val dstAddress =
            when {
                isRuneDeposit -> params.srcAddress
                isRouterDeposit -> checkNotNull(router)
                else -> vaultAddress
            }

        val specificAndUtxo =
            swapGasCalculator.getSpecificAndUtxo(
                srcToken = srcToken,
                srcAddress = params.srcAddress,
                gasFee = params.gasFee,
                isThorchainRouterDeposit = isRouterDeposit,
                dstAddress = if (isRouterDeposit) dstAddress else null,
                memo = if (isRouterDeposit) memo else null,
                tokenAmountValue = if (isRouterDeposit) params.srcTokenValue.value else null,
            )

        val allowance =
            allowanceRepository.getAllowance(
                chain = srcToken.chain,
                contractAddress = srcToken.contractAddress,
                srcAddress = params.srcAddress,
                dstAddress = dstAddress,
            )
        val isApprovalRequired = allowance != null && allowance < params.srcTokenValue.value

        // Minimum payout (the memo's LIM) in the target's natural units, for the "min. payout" row.
        val sourceAmount1e8 = toThorchainFixedPoint(params.srcTokenValue.value, srcToken.decimal)
        val limit1e8 =
            LimitSwapMemo.getLimitAmount(
                sourceAmount = sourceAmount1e8,
                targetPrice = params.targetPrice.stripTrailingZeros().toPlainString(),
            )
        val minPayout = fromThorchainFixedPoint(limit1e8)
        val expectedDstTokenValue =
            TokenValue(
                value = minPayout.movePointRight(dstToken.decimal).toBigInteger(),
                token = dstToken,
            )

        val externalRecipient = params.destinationAddress.takeIf { it != dstToken.address }

        // THORChain charges a resting order the same affiliate and outbound fees as a market swap
        // on the same route, so the confirmation screen shows the quote's breakdown rather than the
        // gas fee it used to pass here — which, read as a destination-token amount by the verify
        // mapper, priced a BTC gas fee as ETH and then added it on top of the Network Fee. Quoted
        // at the market rate while the order fills at its target price, so these are estimates; the
        // cosigner derives its rows from the same quote, so both devices agree.
        val quoteFees = quote?.fees
        val swapFee = quoteFees?.let { dstToken.convertToTokenValue(it.affiliate) }
        val outboundFee = quoteFees?.let { dstToken.convertToTokenValue(it.outbound) }
        // A quote failure is not worth blocking a placement whose memo is already fully determined,
        // so the breakdown degrades to none rather than failing closed. Zero in the destination
        // token, never the gas fee: the fee rows read this as a destination-token amount.
        val estimatedFees =
            quoteFees?.let { dstToken.convertToTokenValue(it.total) }
                ?: TokenValue(value = BigInteger.ZERO, token = dstToken)

        return RegularSwapTransaction(
            limitOrderTargetPrice = params.targetPrice,
            limitOrderExpiryHours = params.expiryHours,
            id = UUID.randomUUID().toString(),
            vaultId = params.vaultId,
            srcToken = srcToken,
            srcTokenValue = params.srcTokenValue,
            dstToken = dstToken,
            dstAddress = dstAddress,
            expectedDstTokenValue = expectedDstTokenValue,
            blockChainSpecific = specificAndUtxo,
            estimatedFees = estimatedFees,
            swapFee = swapFee,
            outboundFee = outboundFee,
            gasFees = params.estimatedNetworkFeeTokenValue ?: params.gasFee,
            memo = memo,
            isApprovalRequired = isApprovalRequired,
            gasFeeFiatValue = params.estimatedNetworkFeeFiatValue ?: params.gasFeeFiatValue,
            externalRecipient = externalRecipient,
            payload =
                SwapPayload.ThorChain(
                    THORChainSwapPayload(
                        fromAddress = params.srcAddress,
                        fromCoin = srcToken,
                        toCoin = dstToken,
                        vaultAddress = vaultAddress,
                        routerAddress = router,
                        fromAmount = params.srcTokenValue.value,
                        toAmountDecimal = minPayout,
                        // The real floor is the memo's LIM (signed verbatim); this proto field is
                        // never read at sign time, so it stays "0" like the market path.
                        toAmountLimit = "0",
                        streamingInterval = "1",
                        streamingQuantity = "0",
                        expirationTime =
                            (params.now.milliseconds + 15.minutes).inWholeSeconds.toULong(),
                        isAffiliate = true,
                    )
                ),
        )
    }

    /**
     * The ordinary THORChain swap quote for this pair and amount, or null when it fails.
     *
     * Supplies the two things a `=<` deposit can't derive on its own: the chain's router contract
     * (set by the node whenever the inbound asset is an ERC20) and the affiliate / outbound fee
     * breakdown. Deliberately requests no referral code and no bps discount, matching what the
     * cosigner can ask for — a discount only the placing device knows about would put the two
     * confirmation screens back out of agreement.
     *
     * A null return fails the ERC20 placement closed (a router-less deposit would strand the
     * tokens) but only costs the fee breakdown on every other route.
     */
    private suspend fun fetchSwapQuote(params: Params): THORChainSwapQuote? =
        try {
            val quote =
                swapQuoteRepository
                    .getQuote(
                        SwapProvider.THORCHAIN,
                        SwapQuoteRequest(
                            srcToken = params.srcToken,
                            dstToken = params.dstToken,
                            tokenValue = params.srcTokenValue,
                            // The vault's own destination address, not a custom recipient: this is
                            // the request the cosigner reconstructs from the payload, and only an
                            // identical request yields identical fees.
                            dstAddress = params.dstToken.address,
                        ),
                    )
                    .expectNative(SwapProvider.THORCHAIN)
            (quote as? SwapQuote.ThorChain)?.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The quote used to be fetched only for ERC20 placements, where a failure fails closed
            // on the missing router. Every route asks for it now, so a failure has to degrade to a
            // missing fee breakdown rather than block a placement whose memo is fully determined.
            Timber.w(e, "THORChain limit-order quote failed")
            null
        }
}
