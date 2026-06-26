package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.increaseByPercent
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber
import vultisig.keysign.v1.TransactionType

class CosmosFeeService(private val cosmosApiFactory: CosmosApiFactory) : FeeService {

    /**
     * Terra Classic burn rate only changes via governance, but [calculateFees] runs on every
     * debounced keystroke during fee display. Cache the parsed rate for a short TTL so the hot path
     * doesn't hit `/terra/tax/v1beta1/params` on each keypress. A single `@Volatile` reference
     * makes the read/publish atomic; concurrent refreshes are harmless (idempotent fetch).
     */
    @Volatile private var cachedBurnTaxRate: CachedBurnTaxRate? = null

    private data class CachedBurnTaxRate(val rate: BigDecimal, val fetchedAtMs: Long)

    companion object {
        internal const val OSMOSIS_MIN_FEE_UOSMO = 25_000L

        private const val BURN_TAX_RATE_TTL_MS = 5 * 60 * 1000L

        // `gasLimit * gasPrice` lands below what Akash's validators accept: the
        // chain-registry price (0.025 uakt/gas) on a ~300k-gas delegation yields
        // only 7500 uakt (0.0075 AKT), which the mempool rejects for insufficient
        // fee. Floor the injected fee to 0.025 AKT — the minimum Akash accepts.
        // Mirrors vultisig-windows `keplrMinInjectedFee.Akash` (PR #4025).
        internal const val AKASH_MIN_FEE_UAKT = 25_000L

        // gasLimit = gasUsed × 1.3 — headroom so the broadcast tx isn't rejected out-of-gas-bound.
        private const val GAS_ADJUSTMENT_PERCENT = 30

        // Chains whose sends WalletCore assembles as a native bank `MsgSend` (routed through
        // CosmosHelper in SigningHelper), so the simulated unsigned tx matches the broadcast tx.
        private val SIMULATION_SUPPORTED_CHAINS =
            setOf(
                Chain.GaiaChain,
                Chain.Kujira,
                Chain.Dydx,
                Chain.Osmosis,
                Chain.Noble,
                Chain.Akash,
            )
    }

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        return calculateDefaultFees(transaction)
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val staticLimit = CosmosHelper.getChainGasLimit(chain)
        val staticFees = staticGasFees(transaction, chain, staticLimit)

        // Derive gas from an actual `/cosmos/tx/simulate`; fall back to the static per-chain values
        // when simulation is unavailable (issue #4847).
        val gasUsed = simulatedGasUsed(transaction) ?: return staticFees

        val limit = gasUsed.toBigInteger().increaseByPercent(GAS_ADJUSTMENT_PERCENT)
        // Reuse the static fee/limit ratio as the chain's gas price so the simulated gas scales the
        // fee while preserving each chain's economics; floor it at the chain minimum.
        val gasPrice =
            staticFees.amount
                .toBigDecimal()
                .divide(staticLimit.toBigDecimal(), MathContext.DECIMAL64)
        val amount =
            (limit.toBigDecimal() * gasPrice)
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger()
                .max(minFeeFloor(chain))
        return GasFees(limit = limit, amount = amount)
    }

    /**
     * Static per-chain gas limit + fee amount, used as the fallback when `/simulate` is unavailable
     * and as the gas-price source for the simulated path. Mirrors the previously-hardcoded values.
     */
    private suspend fun staticGasFees(
        transaction: BlockchainTransaction,
        chain: Chain,
        gasLimit: Long,
    ): GasFees =
        when (chain) {
            Chain.Osmosis ->
                // Osmosis uses EIP-1559 dynamic fees; OSMOSIS_MIN_FEE_UOSMO matches iOS and covers
                // the 300k gas × 0.03 uosmo/gas minimum with headroom for base fee spikes.
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = OSMOSIS_MIN_FEE_UOSMO.toBigInteger(),
                )
            Chain.Akash ->
                GasFees(limit = gasLimit.toBigInteger(), amount = AKASH_MIN_FEE_UAKT.toBigInteger())
            Chain.GaiaChain,
            Chain.Kujira,
            Chain.Terra,
            Chain.Qbtc -> GasFees(limit = gasLimit.toBigInteger(), amount = 7500.toBigInteger())
            Chain.Noble -> GasFees(limit = gasLimit.toBigInteger(), amount = 20000L.toBigInteger())
            Chain.TerraClassic ->
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = terraClassicFeeAmount(transaction),
                )
            Chain.Dydx ->
                GasFees(limit = gasLimit.toBigInteger(), amount = 2500000000000000L.toBigInteger())
            else -> error("Chain Not Supported: ${chain.name}")
        }

    /** Minimum fee a chain's mempool accepts regardless of simulated gas, or zero when none. */
    private fun minFeeFloor(chain: Chain): BigInteger =
        when (chain) {
            Chain.Osmosis -> OSMOSIS_MIN_FEE_UOSMO.toBigInteger()
            Chain.Akash -> AKASH_MIN_FEE_UAKT.toBigInteger()
            else -> BigInteger.ZERO
        }

    /**
     * Simulates the unsigned transaction and returns `gas_info.gas_used`, or `null` to fall back to
     * the static gas limit. Only the chains whose sends WalletCore builds as native bank `MsgSend`
     * are simulated; Terra/TerraClassic (TerraHelper, burn tax) and Qbtc (ML-DSA) keep their static
     * values.
     */
    private suspend fun simulatedGasUsed(transaction: BlockchainTransaction): Long? {
        val coin = transaction.coin
        val chain = coin.chain
        if (chain !in SIMULATION_SUPPORTED_CHAINS) return null
        if (transaction !is Transfer) return null
        return try {
            val staticLimit = CosmosHelper.getChainGasLimit(chain)
            val txBytes =
                CosmosHelper(
                        coinType = coin.coinType,
                        denom = chain.feeUnit,
                        gasLimit = staticLimit,
                    )
                    .getZeroSignedTransaction(buildSimulationPayload(transaction, staticLimit))
            cosmosApiFactory.createCosmosApi(chain).simulate(txBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cosmos simulate failed; falling back to static gas")
            null
        }
    }

    /**
     * Minimal keysign payload (account/sequence are irrelevant to simulation) for a native send.
     */
    private fun buildSimulationPayload(transaction: Transfer, gasLimit: Long): KeysignPayload =
        KeysignPayload(
            coin = transaction.coin,
            toAddress = transaction.to,
            toAmount = transaction.amount,
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    gas = gasLimit.toBigInteger(),
                    ibcDenomTraces = null,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            memo = transaction.memo,
            vaultPublicKeyECDSA = transaction.vault.vaultHexPublicKey,
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    /**
     * Terra Classic fee = base gas + proportional burn tax. The base gas is denominated in the same
     * denom the signer uses for the fee (uluna for native LUNC / CW20 / IBC, uusd for the USTC bank
     * denom — see [TerraClassicTax.baseGas] and `TerraHelper`). The burn tax (~0.5%, fetched live
     * with a conservative fallback) is folded into this single fee field only when it is paid in
     * the send denom (native LUNC or USTC); CW20/IBC sends, whose fee is in uluna while the send is
     * in the token's own denom, get base gas only to avoid mixing denoms.
     */
    private suspend fun terraClassicFeeAmount(transaction: BlockchainTransaction): BigInteger {
        val coin = transaction.coin
        val base = TerraClassicTax.baseGas(coin.contractAddress, coin.isNativeToken).toBigInteger()

        val taxable =
            transaction is Transfer &&
                TerraClassicTax.taxPaidInSendDenom(coin.contractAddress, coin.isNativeToken) &&
                transaction.amount > BigInteger.ZERO
        if (!taxable) return base

        return base + TerraClassicTax.burnTax(transaction.amount, burnTaxRate())
    }

    private suspend fun burnTaxRate(): BigDecimal {
        val now = System.currentTimeMillis()
        cachedBurnTaxRate?.let { if (now - it.fetchedAtMs < BURN_TAX_RATE_TTL_MS) return it.rate }
        val rawRate =
            cosmosApiFactory.createCosmosApi(Chain.TerraClassic).getTerraClassicBurnTaxRate()
        val rate = TerraClassicTax.parseRate(rawRate)
        cachedBurnTaxRate = CachedBurnTaxRate(rate, now)
        return rate
    }
}
