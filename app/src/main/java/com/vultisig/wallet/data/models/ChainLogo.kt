package com.vultisig.wallet.data.models

import com.vultisig.wallet.R

internal val Chain.logo: Int
    get() = when (this) {
        Chain.ThorChain -> R.drawable.rune
        Chain.Solana -> R.drawable.solana
        Chain.Ethereum -> R.drawable.ethereum
        Chain.Avalanche -> R.drawable.avax
        Chain.Base -> R.drawable.base
        Chain.Blast -> R.drawable.blast
        Chain.Arbitrum -> R.drawable.arbitrum
        Chain.Polygon -> R.drawable.polygon
        Chain.Optimism -> R.drawable.optimism
        Chain.BscChain -> R.drawable.bsc
        Chain.Bitcoin -> R.drawable.bitcoin
        Chain.BitcoinCash -> R.drawable.bitcoincash
        Chain.Litecoin -> R.drawable.litecoin
        Chain.Dogecoin -> R.drawable.doge
        Chain.Dash -> R.drawable.dash
        Chain.GaiaChain -> R.drawable.atom
        Chain.Kujira -> R.drawable.kuji
        Chain.MayaChain -> R.drawable.cacao
        Chain.CronosChain -> R.drawable.cro
        Chain.Polkadot -> R.drawable.dot
        Chain.Dydx -> R.drawable.dydx
        Chain.ZkSync -> R.drawable.zksync
        Chain.Sui -> R.drawable.sui
        Chain.Ton -> R.drawable.ton
        Chain.Osmosis -> R.drawable.osmo
        Chain.Terra -> R.drawable.luna
        Chain.TerraClassic -> R.drawable.lunc
        Chain.Noble -> R.drawable.noble
        Chain.Ripple -> R.drawable.xrp
        Chain.Akash -> R.drawable.akash
        Chain.Tron -> R.drawable.tron
        Chain.Zcash -> R.drawable.zcash
        Chain.Cardano -> R.drawable.cardano
        Chain.Mantle -> R.drawable.mantle
    }