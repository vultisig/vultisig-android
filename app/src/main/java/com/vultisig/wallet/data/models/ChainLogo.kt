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
        Chain.MayaChain -> R.drawable.maya
        Chain.CronosChain -> R.drawable.cro
        Chain.Polkadot -> R.drawable.dot
        Chain.Dydx -> R.drawable.dydx
        Chain.ZkSync -> R.drawable.zksync
        Chain.Sui -> R.drawable.sui
        Chain.Ton -> R.drawable.ton
        Chain.Osmosis -> R.drawable.osmo
        Chain.Terra -> R.drawable.terra
        Chain.TerraClassic -> R.drawable.terra_classic
        Chain.Noble -> R.drawable.noble
        Chain.Ripple -> R.drawable.xrp
        Chain.Akash -> R.drawable.akash
        Chain.Tron -> R.drawable.tron
        Chain.Zcash -> R.drawable.zcash
        Chain.Cardano -> R.drawable.cardano
        Chain.Mantle -> R.drawable.mantle
        Chain.Sei -> R.drawable.sei
    }

internal val Chain.monoToneLogo: Int
    get() = when (this) {
        Chain.ThorChain -> R.drawable.rune_mono
        Chain.Solana -> R.drawable.sol_mono
        Chain.Ethereum -> R.drawable.eth_mono
        Chain.Avalanche -> R.drawable.ava_mono
        Chain.Base -> R.drawable.base_mono
        Chain.Blast -> R.drawable.blast_mono
        Chain.Arbitrum -> R.drawable.arb_mono
        Chain.Polygon -> R.drawable.pol_mono
        Chain.Optimism -> R.drawable.opti_mono
        Chain.BscChain -> R.drawable.bsc_mono
        Chain.Bitcoin -> R.drawable.btc_mono
        Chain.BitcoinCash -> R.drawable.bch_mono
        Chain.Litecoin -> R.drawable.ltc_mono
        Chain.Dogecoin -> R.drawable.doge_mono
        Chain.Dash -> R.drawable.dash_mono
        Chain.GaiaChain -> R.drawable.cosmos_mono
        Chain.Kujira -> R.drawable.kuji_mono
        Chain.MayaChain -> R.drawable.maya_mono
        Chain.CronosChain -> R.drawable.cronos_mono
        Chain.Polkadot -> R.drawable.dot_mono
        Chain.Dydx -> R.drawable.dxdy_mono
        Chain.ZkSync -> R.drawable.zksync_mono
        Chain.Sui -> R.drawable.sui_mono
        Chain.Ton -> R.drawable.ton_mono
        Chain.Osmosis -> R.drawable.osmosis_mono
        Chain.Terra -> R.drawable.terra_mono
        Chain.TerraClassic -> R.drawable.terra_c_mono
        Chain.Noble -> R.drawable.noble_mono
        Chain.Ripple -> R.drawable.xrp_mono
        Chain.Akash -> R.drawable.akash_mono
        Chain.Tron -> R.drawable.tron_mono
        Chain.Zcash -> R.drawable.zcash_mono
        Chain.Cardano -> R.drawable.cardano_mono
        Chain.Mantle -> R.drawable.mantl_mono
        Chain.Sei -> R.drawable.sei_mono
    }