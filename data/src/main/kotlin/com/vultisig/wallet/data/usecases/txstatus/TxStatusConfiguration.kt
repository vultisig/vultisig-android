package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

data class TxStatusConfiguration(
    val pollIntervalSeconds: Long,
    val maxWaitMinutes: Long
)

interface TxStatusConfigurationProvider {
    fun getConfigurationForChain(chain: Chain): TxStatusConfiguration
}

internal class TxStatusConfigurationProviderFakeImpl @Inject constructor(): TxStatusConfigurationProvider{
    override fun getConfigurationForChain(chain: Chain): TxStatusConfiguration {
        return TxStatusConfiguration(5,1)
    }
}

internal class TxStatusConfigurationProviderImpl @Inject constructor() : TxStatusConfigurationProvider {

    private val txStatusConfigs = mapOf(
        Chain.Ethereum to TxStatusConfiguration(5, 10),
        Chain.Polygon to TxStatusConfiguration(5, 10),
        Chain.Arbitrum to TxStatusConfiguration(5, 10),
        Chain.Base to TxStatusConfiguration(5, 10),
        Chain.Avalanche to TxStatusConfiguration(5, 10),
        Chain.CronosChain to TxStatusConfiguration(5, 10),
        Chain.BscChain to TxStatusConfiguration(5, 10),
        Chain.Blast to TxStatusConfiguration(5, 10),
        Chain.Optimism to TxStatusConfiguration(5, 10),
        Chain.ZkSync to TxStatusConfiguration(5, 10),
        Chain.Mantle to TxStatusConfiguration(5, 10),
        Chain.Sei to TxStatusConfiguration(5, 10),
        Chain.Hyperliquid to TxStatusConfiguration(5, 10),
        Chain.Bitcoin to TxStatusConfiguration(30, 120),
        Chain.BitcoinCash to TxStatusConfiguration(30, 120),
        Chain.Litecoin to TxStatusConfiguration(15, 30),
        Chain.Dogecoin to TxStatusConfiguration(10, 20),
        Chain.Dash to TxStatusConfiguration(15, 30),
        Chain.Zcash to TxStatusConfiguration(15, 30),
        Chain.Cardano to TxStatusConfiguration(5, 10),
        Chain.GaiaChain to TxStatusConfiguration(3, 5),
        Chain.Kujira to TxStatusConfiguration(3, 5),
        Chain.Dydx to TxStatusConfiguration(3, 5),
        Chain.Osmosis to TxStatusConfiguration(3, 5),
        Chain.Terra to TxStatusConfiguration(3, 5),
        Chain.TerraClassic to TxStatusConfiguration(3, 5),
        Chain.Noble to TxStatusConfiguration(3, 5),
        Chain.Akash to TxStatusConfiguration(3, 5),
        Chain.ThorChain to TxStatusConfiguration(3, 5),
        Chain.MayaChain to TxStatusConfiguration(3, 5),
        Chain.Solana to TxStatusConfiguration(2, 2),
        Chain.Sui to TxStatusConfiguration(2, 2),
        Chain.Ton to TxStatusConfiguration(3, 5),
        Chain.Polkadot to TxStatusConfiguration(3, 5),
        Chain.Ripple to TxStatusConfiguration(2, 5),
        Chain.Tron to TxStatusConfiguration(2, 5)
    )

    override fun getConfigurationForChain(chain: Chain): TxStatusConfiguration {
        return txStatusConfigs[chain]
            ?: throw IllegalArgumentException("Chain ${chain.name} is not supported")
    }

}