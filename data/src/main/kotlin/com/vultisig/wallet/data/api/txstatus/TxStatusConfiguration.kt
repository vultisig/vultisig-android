package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard

data class TxStatusConfiguration(
    val pollIntervalMs: Long,
    val maxWaitMs: Long
) {
    companion object {
        fun getConfig(chain: Chain): TxStatusConfiguration {
            return when {
                chain.standard == TokenStandard.EVM -> TxStatusConfiguration(
                    pollIntervalMs = 5000,
                    maxWaitMs = 600000
                )

                chain.standard == TokenStandard.UTXO -> TxStatusConfiguration(
                    pollIntervalMs = 30000,
                    maxWaitMs = 7200000
                )

                chain.standard == TokenStandard.COSMOS -> TxStatusConfiguration(
                    pollIntervalMs = 3000,
                    maxWaitMs = 300000
                )
                chain == Chain.Solana -> TxStatusConfiguration(
                    pollIntervalMs = 2000,
                    maxWaitMs = 120000
                )
                chain == Chain.Sui -> TxStatusConfiguration(
                    pollIntervalMs = 2000,
                    maxWaitMs = 120000
                )
                chain == Chain.Ton -> TxStatusConfiguration(
                    pollIntervalMs = 3000,
                    maxWaitMs = 300000
                )
                chain == Chain.Polkadot -> TxStatusConfiguration(
                    pollIntervalMs = 3000,
                    maxWaitMs = 300000
                )
                chain == Chain.Ripple -> TxStatusConfiguration(
                    pollIntervalMs = 2000,
                    maxWaitMs = 300000
                )
                chain == Chain.Tron -> TxStatusConfiguration(
                    pollIntervalMs = 2000,
                    maxWaitMs = 300000
                )

                else -> {
                    TxStatusConfiguration(
                        pollIntervalMs = 5000,
                        maxWaitMs = 600000
                    )
                }
            }
        }
    }
}