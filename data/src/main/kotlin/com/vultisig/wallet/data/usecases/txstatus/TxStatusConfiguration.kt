package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

data class TxStatusConfiguration(
    val pollIntervalSeconds: Long,
    val maxWaitSeconds: Long
)

interface TxStatusConfigurationProvider {
    fun getConfigurationForChain(chain: Chain): TxStatusConfiguration
    fun supportTxStatus(chain: Chain): Boolean
}

internal class TxStatusConfigurationProviderImpl @Inject constructor() : TxStatusConfigurationProvider {

    private val txStatusConfigs = mapOf(
        Chain.Ethereum to TxStatusConfiguration(5, 5 * 60),
        Chain.Polygon to TxStatusConfiguration(5, 5 * 60),
        Chain.Arbitrum to TxStatusConfiguration(5, 5 * 60),
        Chain.Base to TxStatusConfiguration(5, 5 * 60),
        Chain.Avalanche to TxStatusConfiguration(5, 5 * 60),
        Chain.CronosChain to TxStatusConfiguration(5, 5 * 60),
        Chain.BscChain to TxStatusConfiguration(5, 5 * 60),
        Chain.Blast to TxStatusConfiguration(5, 5 * 60),
        Chain.Optimism to TxStatusConfiguration(5, 5 * 60),
        Chain.ZkSync to TxStatusConfiguration(5, 5 * 60),
        Chain.Mantle to TxStatusConfiguration(5, 5 * 60),
        Chain.Sei to TxStatusConfiguration(5, 5 * 60),
        Chain.Hyperliquid to TxStatusConfiguration(5, 5 * 60),

        Chain.Bitcoin to TxStatusConfiguration(30, 120 * 60),
        Chain.BitcoinCash to TxStatusConfiguration(30, 120 * 60),
        Chain.Litecoin to TxStatusConfiguration(15, 30 * 60),
        Chain.Dogecoin to TxStatusConfiguration(10, 20 * 60),
        Chain.Dash to TxStatusConfiguration(15, 30 * 60),
        Chain.Zcash to TxStatusConfiguration(15, 30 * 60),

        Chain.Cardano to TxStatusConfiguration(5, 10 * 60),

        Chain.GaiaChain to TxStatusConfiguration(3, 5 * 60),
        Chain.Kujira to TxStatusConfiguration(3, 5 * 60),
        Chain.Dydx to TxStatusConfiguration(3, 5 * 60),
        Chain.Osmosis to TxStatusConfiguration(3, 5 * 60),
        Chain.Terra to TxStatusConfiguration(3, 5 * 60),
        Chain.TerraClassic to TxStatusConfiguration(3, 5 * 60),
        Chain.Noble to TxStatusConfiguration(3, 5 * 60),
        Chain.Akash to TxStatusConfiguration(3, 5 * 60),
        Chain.ThorChain to TxStatusConfiguration(3, 5 * 60),
        Chain.MayaChain to TxStatusConfiguration(3, 5 * 60),

        Chain.Solana to TxStatusConfiguration(2, 2 * 60),
        Chain.Sui to TxStatusConfiguration(2, 2 * 60),

        Chain.Ton to TxStatusConfiguration(3, 5 * 60),
        Chain.Polkadot to TxStatusConfiguration(3, 5 * 60),

        Chain.Ripple to TxStatusConfiguration(2, 5 * 60),
        Chain.Tron to TxStatusConfiguration(2, 5 * 60)
    )

    override fun supportTxStatus(chain: Chain): Boolean {
        return chain in txStatusConfigs.keys
    }


    override fun getConfigurationForChain(chain: Chain): TxStatusConfiguration {
        return txStatusConfigs[chain]
            ?: throw IllegalArgumentException("Chain ${chain.name} is not supported")
    }

}