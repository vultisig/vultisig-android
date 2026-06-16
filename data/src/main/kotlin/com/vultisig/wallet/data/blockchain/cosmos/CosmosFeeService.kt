package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.models.Chain
import java.math.BigDecimal
import java.math.BigInteger

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
    }

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        return calculateDefaultFees(transaction)
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val gasLimit =
            when (chain) {
                Chain.Qbtc, // ML-DSA-44 signatures are ~2.4 KB, needs more gas
                Chain.Terra,
                Chain.TerraClassic,
                Chain.Osmosis -> 300000L
                else -> 200000L
            }
        return when (chain) {
            Chain.Osmosis -> {
                // Osmosis uses EIP-1559 dynamic fees; OSMOSIS_MIN_FEE_UOSMO matches iOS and covers
                // the 300k gas × 0.03 uosmo/gas minimum with headroom for base fee spikes.
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = OSMOSIS_MIN_FEE_UOSMO.toBigInteger(),
                )
            }
            Chain.Akash -> {
                GasFees(limit = gasLimit.toBigInteger(), amount = AKASH_MIN_FEE_UAKT.toBigInteger())
            }
            Chain.GaiaChain,
            Chain.Kujira,
            Chain.Terra,
            Chain.Qbtc -> {
                GasFees(limit = gasLimit.toBigInteger(), amount = 7500.toBigInteger())
            }
            Chain.Noble -> {
                GasFees(limit = gasLimit.toBigInteger(), amount = 20000L.toBigInteger())
            }
            Chain.TerraClassic -> {
                GasFees(
                    limit = gasLimit.toBigInteger(),
                    amount = terraClassicFeeAmount(transaction),
                )
            }
            Chain.Dydx -> {
                GasFees(limit = gasLimit.toBigInteger(), amount = 2500000000000000L.toBigInteger())
            }
            else -> error("Chain Not Supported: ${chain.name}")
        }
    }

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
