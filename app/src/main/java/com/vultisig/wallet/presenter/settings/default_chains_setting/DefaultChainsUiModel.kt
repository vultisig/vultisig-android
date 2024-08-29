package com.vultisig.wallet.presenter.settings.default_chains_setting

import androidx.annotation.DrawableRes
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo

data class DefaultChainsUiModel(
    val chains: List<DefaultChain> = emptyList(),
    val selectedDefaultChains: List<DefaultChain> = emptyList(),
)

data class DefaultChain(
    val title: String,
    val subtitle: String,
    @DrawableRes val logo: Int
)

internal fun Chain.toUiModel() = DefaultChain(title = raw, subtitle = Ticker, logo = logo)

internal fun DefaultChain.toDataModel() = Chain.entries.first { it.raw == title }

internal fun List<Chain>.toUiModel() = map { it.toUiModel() }

private val Chain.Ticker: String
    get() = when (this) {
        Chain.ThorChain -> "RUNE"
        Chain.Solana -> "SOL"
        Chain.Ethereum -> "ETH"
        Chain.Avalanche -> "AVAX"
        Chain.Base -> "BASE"
        Chain.Blast -> "BLAST"
        Chain.Arbitrum -> "ARB"
        Chain.Polygon -> "MATIC"
        Chain.Optimism -> "OP"
        Chain.BscChain -> "BNB"
        Chain.Bitcoin -> "BTC"
        Chain.BitcoinCash -> "BCH"
        Chain.Litecoin -> "LTC"
        Chain.Dogecoin -> "DOGE"
        Chain.Dash -> "DASH"
        Chain.GaiaChain -> "UATOM"
        Chain.Kujira -> "KUJI"
        Chain.MayaChain -> "CACAO"
        Chain.CronosChain -> "CRO"
        Chain.Polkadot -> "DOT"
        Chain.Dydx -> "DYDX"
        Chain.ZkSync -> "ZK"
    }