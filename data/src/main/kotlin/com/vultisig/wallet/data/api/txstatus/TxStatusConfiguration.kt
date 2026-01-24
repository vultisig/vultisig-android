package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard

data class TxStatusConfiguration(
    val estimatedTime: String,
    val pollIntervalMs: Long,
    val maxWaitMs: Long
) {
    companion object {
        fun getConfig(chain: Chain): TxStatusConfiguration {
            return when {
                chain.standard == TokenStandard.EVM -> TxStatusConfiguration(
                    "~15-30 sec",
                    5000,
                    600000
                )

                chain.standard == TokenStandard.UTXO -> TxStatusConfiguration(
                    "~10-60 min",
                    30000,
                    7200000
                )

                chain.standard == TokenStandard.COSMOS -> TxStatusConfiguration("~6 sec", 3000, 300000)
                chain == Chain.Solana -> TxStatusConfiguration("~1-2 sec", 2000, 120000)
                chain == Chain.Sui -> TxStatusConfiguration("~2-3 sec", 2000, 120000)
                chain == Chain.Ton -> TxStatusConfiguration("~5 sec", 3000, 300000)
                chain == Chain.Polkadot -> TxStatusConfiguration("~6 sec", 3000, 300000)
                chain == Chain.Ripple -> TxStatusConfiguration("~3-5 sec", 2000, 300000)
                chain == Chain.Tron -> TxStatusConfiguration("~3 sec", 2000, 300000)
                else -> error("Unknown chain: $chain")
            }
        }
    }
}