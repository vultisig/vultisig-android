package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.models.Chain

class CosmosFeeService : FeeService {

    companion object {
        internal const val OSMOSIS_MIN_FEE_UOSMO = 25_000L

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
                GasFees(limit = gasLimit.toBigInteger(), amount = 10000000L.toBigInteger())
            }
            Chain.Dydx -> {
                GasFees(limit = gasLimit.toBigInteger(), amount = 2500000000000000L.toBigInteger())
            }
            else -> error("Chain Not Supported: ${chain.name}")
        }
    }
}
