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

    /**
     * Simulated `gas_used` for a native bank send is effectively constant per chain (it doesn't
     * vary with the amount or recipient), so cache it for a short TTL to keep the debounced fee
     * path off `getAccountNumber`/`/simulate` on every keystroke — the same reason
     * [cachedBurnTaxRate] exists. A single `@Volatile` reference makes the read/publish atomic; a
     * stale entry is harmless given the [GAS_ADJUSTMENT_PERCENT] headroom.
     */
    @Volatile private var cachedSimulatedGas: CachedSimulatedGas? = null

    private data class CachedSimulatedGas(
        val chain: Chain,
        val gasUsed: Long,
        val fetchedAtMs: Long,
    )

    companion object {
        internal const val OSMOSIS_MIN_FEE_UOSMO = 25_000L

        private const val BURN_TAX_RATE_TTL_MS = 5 * 60 * 1000L

        private const val SIMULATED_GAS_TTL_MS = 60 * 1000L

        // `gasLimit * gasPrice` lands below what Akash's validators accept: the
        // chain-registry price (0.025 uakt/gas) on a ~300k-gas delegation yields
        // only 7500 uakt (0.0075 AKT), which the mempool rejects for insufficient
        // fee. Floor the injected fee to 0.025 AKT — the minimum Akash accepts.
        // Mirrors vultisig-windows `keplrMinInjectedFee.Akash` (PR #4025).
        internal const val AKASH_MIN_FEE_UAKT = 25_000L

        // gasLimit = gasUsed × 1.3 — headroom so the broadcast tx isn't rejected out-of-gas-bound.
        private const val GAS_ADJUSTMENT_PERCENT = 30

        // Osmosis/Akash static fees are flat minimum-fee floors, not per-gas rates. The simulated
        // path sizes the fee from the chains' real mainnet min gas price (0.025) and lets the
        // static-fee floor cap the result instead of treating the floor as a rate.
        private val OSMOSIS_MIN_GAS_PRICE = BigDecimal("0.025")
        private val AKASH_MIN_GAS_PRICE = BigDecimal("0.025")

        // Terra (LUNA, phoenix-1) minimum gas price, 0.025 uluna/gas. Unlike the flat floors above
        // this is a genuine per-gas rate: at the static 300k limit it is the 7500 uluna fee, so
        // deriving the amount from the limit keeps `fee.amount` consistent with a relayed gas limit
        // honored in `CosmosHelper.buildCosmosFee` (Cosmos requires `fee.amount ≥ gasPrice × gas`).
        private val TERRA_GAS_PRICE = BigDecimal("0.025")

        /**
         * Terra (LUNA) fee amount for [gasLimit], `0.025 uluna/gas × gasLimit` rounded up. Returns
         * the flat 7500 uluna at the static 300k limit; scales with a relayed limit.
         */
        internal fun terraFeeAmount(gasLimit: Long): BigInteger =
            (TERRA_GAS_PRICE * gasLimit.toBigDecimal())
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger()

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
        val amount =
            (limit.toBigDecimal() * simulatedGasPrice(chain, staticFees, staticLimit))
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger()
                // The broadcast tx still declares the static `getChainGasLimit`, so the fee must
                // cover at least the static per-chain amount (`minGasPrice × staticLimit`) or the
                // mempool rejects it; never drop below that floor (issue #4847).
                .max(staticFees.amount)
        return GasFees(limit = limit, amount = amount)
    }

    /**
     * Static per-chain gas limit + fee amount, used as the fallback when `/simulate` is unavailable
     * and as the gas-price source for the simulated path.
     */
    private suspend fun staticGasFees(
        transaction: BlockchainTransaction,
        chain: Chain,
        gasLimit: Long,
    ): GasFees =
        when (chain) {
            Chain.Osmosis ->
                // Osmosis uses EIP-1559 dynamic fees; OSMOSIS_MIN_FEE_UOSMO matches iOS and covers
                // the 300k gas × 0.025 uosmo/gas minimum with headroom for base fee spikes.
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = OSMOSIS_MIN_FEE_UOSMO.toBigInteger(),
                )
            Chain.Akash ->
                GasFees(limit = gasLimit.toBigInteger(), amount = AKASH_MIN_FEE_UAKT.toBigInteger())
            Chain.GaiaChain,
            Chain.Kujira,
            Chain.Qbtc -> GasFees(limit = gasLimit.toBigInteger(), amount = 7500.toBigInteger())
            Chain.Terra ->
                GasFees(limit = gasLimit.toBigInteger(), amount = terraFeeAmount(gasLimit))
            Chain.Noble -> GasFees(limit = gasLimit.toBigInteger(), amount = 20000L.toBigInteger())
            Chain.TerraClassic ->
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = terraClassicFeeAmount(transaction, gasLimit),
                )
            Chain.Dydx ->
                GasFees(limit = gasLimit.toBigInteger(), amount = 2500000000000000L.toBigInteger())
            else -> error("Chain Not Supported: ${chain.name}")
        }

    /**
     * Per-gas price (in the chain's fee denom) used to size the fee from simulated gas. Chains
     * whose [staticGasFees] amount is a flat minimum-fee floor rather than a per-gas rate (Osmosis,
     * Akash) expose their real min gas price here, so the static-fee floor in
     * [calculateDefaultFees] only caps the result instead of inflating it; for every other chain
     * the static `amount / limit` ratio already is the per-gas rate.
     */
    private fun simulatedGasPrice(
        chain: Chain,
        staticFees: GasFees,
        staticLimit: Long,
    ): BigDecimal =
        when (chain) {
            Chain.Osmosis -> OSMOSIS_MIN_GAS_PRICE
            Chain.Akash -> AKASH_MIN_GAS_PRICE
            else ->
                staticFees.amount
                    .toBigDecimal()
                    .divide(staticLimit.toBigDecimal(), MathContext.DECIMAL64)
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
        val now = System.currentTimeMillis()
        cachedSimulatedGas?.let {
            if (it.chain == chain && now - it.fetchedAtMs < SIMULATED_GAS_TTL_MS) return it.gasUsed
        }
        return try {
            val staticLimit = CosmosHelper.getChainGasLimit(chain)
            val api = cosmosApiFactory.createCosmosApi(chain)
            // `/simulate` runs the AnteHandler, which rejects a stale sequence with an "account
            // sequence mismatch", so seed the unsigned tx with the live on-chain account state
            // instead of zeros — otherwise every funded account silently falls back to static gas.
            val account = api.getAccountNumber(coin.address)
            val txBytes =
                CosmosHelper(
                        coinType = coin.coinType,
                        denom = chain.feeUnit,
                        gasLimit = staticLimit,
                    )
                    .getZeroSignedTransaction(
                        buildSimulationPayload(
                            transaction = transaction,
                            accountNumber = BigInteger(account.accountNumber ?: "0"),
                            sequence = BigInteger(account.sequence ?: "0"),
                        )
                    )
            api.simulate(txBytes)?.also { cachedSimulatedGas = CachedSimulatedGas(chain, it, now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cosmos simulate failed; falling back to static gas")
            null
        }
    }

    /**
     * Minimal keysign payload for a native send. [accountNumber] and [sequence] must be the live
     * on-chain values because `/simulate` runs the AnteHandler and rejects a stale sequence. The
     * fee amount is left at zero so the AnteHandler's DeductFee charges nothing — otherwise a
     * declared fee fails MAX / near-balance sends and silently reverts to the static fee.
     */
    private fun buildSimulationPayload(
        transaction: Transfer,
        accountNumber: BigInteger,
        sequence: BigInteger,
    ): KeysignPayload =
        KeysignPayload(
            coin = transaction.coin,
            toAddress = transaction.to,
            toAmount = transaction.amount,
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = accountNumber,
                    sequence = sequence,
                    gas = BigInteger.ZERO,
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
    private suspend fun terraClassicFeeAmount(
        transaction: BlockchainTransaction,
        gasLimit: Long,
    ): BigInteger {
        val coin = transaction.coin
        val base = TerraClassicTax.baseGas(coin.contractAddress, coin.isNativeToken, gasLimit)

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
