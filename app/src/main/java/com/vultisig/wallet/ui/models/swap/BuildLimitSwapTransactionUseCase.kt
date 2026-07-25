package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import com.vultisig.wallet.data.swap.limit.LimitSwapMemo
import com.vultisig.wallet.data.swap.limit.buildLimitSwapMemoForCoins
import com.vultisig.wallet.data.swap.limit.fromThorchainFixedPoint
import com.vultisig.wallet.data.swap.limit.thorchainMemoAssetChainPrefix
import com.vultisig.wallet.data.swap.limit.toThorchainFixedPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Assembles the [RegularSwapTransaction] for a THORChain limit order.
 *
 * Mirrors the Vultisig SDK's `buildLimitSwapKeysignPayload`, reusing the market swap's keysign
 * pipeline (`SwapPayload.ThorChain` + memo → verify → keysign). The only substitutions are a
 * locally-built `=<` memo instead of a server quote memo, and inbound/router addresses fetched
 * directly (a limit order never requests a market quote).
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
) {

    private val fiatFormat = DecimalFormat("#,##0.00")
    private val assetFormat = DecimalFormat("#,##0.########")

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

        val router = inbound?.router?.takeIf { it.isNotBlank() }
        // An EVM ERC20 must deposit through the router's depositWithExpiry (which carries the
        // memo).
        // With no router it would fall back to a plain transfer that drops the memo and strands the
        // tokens on the inbound vault — never a protected limit order — so fail closed instead.
        if (isEvmToken && router == null) {
            error(
                "THORChain has no router for ${srcToken.chain}; cannot place an ERC20 limit order"
            )
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

        return RegularSwapTransaction(
            limitOrderTargetPriceLabel = targetPriceLabel(srcToken, dstToken, params.targetPrice),
            limitOrderExpiryLabel = expiryLabel(params.expiryHours),
            id = UUID.randomUUID().toString(),
            vaultId = params.vaultId,
            srcToken = srcToken,
            srcTokenValue = params.srcTokenValue,
            dstToken = dstToken,
            dstAddress = dstAddress,
            expectedDstTokenValue = expectedDstTokenValue,
            blockChainSpecific = specificAndUtxo,
            estimatedFees = params.estimatedNetworkFeeTokenValue ?: params.gasFee,
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

    /** "1 <buy> = $65,800.13" (fiat when the sell price is known, else in sell-asset units). */
    private fun targetPriceLabel(srcToken: Coin, dstToken: Coin, targetPrice: BigDecimal): String {
        val fiat = LimitOrderPricing.fiatPricePerBuyUnit(targetPrice, srcToken.usdPrice)
        return if (fiat != null) {
            "1 ${dstToken.ticker} = $${fiatFormat.format(fiat)}"
        } else {
            val sellPerBuy =
                if (targetPrice.signum() > 0) {
                    BigDecimal.ONE.divide(targetPrice, 8, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }
            "1 ${dstToken.ticker} = ${assetFormat.format(sellPerBuy)} ${srcToken.ticker}"
        }
    }

    private fun expiryLabel(expiryHours: Int): String =
        when (expiryHours) {
            72 -> "3d"
            else -> "${expiryHours}h"
        }
}
