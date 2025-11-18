package com.vultisig.wallet.data.models

import com.vultisig.wallet.R

internal fun getCoinLogo(logoName: String): ImageModel {
    return when (logoName.lowercase()) {
        "btc" -> R.drawable.bitcoin
        "bch" -> R.drawable.bitcoincash
        "ltc" -> R.drawable.litecoin
        "doge" -> R.drawable.doge
        "dash" -> R.drawable.dash
        "rune" -> R.drawable.rune
        "eth" -> R.drawable.ethereum
        "sol", "solana" -> R.drawable.solana
        "cacao" -> R.drawable.cacao
        "maya" -> R.drawable.maya_token_02
        "usdc" -> R.drawable.usdc
        "usdt" -> R.drawable.usdt
        "link" -> R.drawable.link
        "uni" -> R.drawable.uni
        "pol", "matic" -> R.drawable.polygon
        "wbtc" -> R.drawable.wbtc
        "flip" -> R.drawable.chainflip
        "avax" -> R.drawable.avax
        "optimism" -> R.drawable.optimism
        "eth_polygon" -> R.drawable.polygon
        "bsc" -> R.drawable.bsc
        "blast" -> R.drawable.blast
        "cro" -> R.drawable.cro
        "arbitrum" -> R.drawable.arbitrum
        "kuji" -> R.drawable.kuji
        "atom" -> R.drawable.atom
        "dydx" -> R.drawable.dydx
        "polygon" -> R.drawable.polygon
        "tgt" -> R.drawable.tgt
        "fox" -> R.drawable.fox
        "dot" -> R.drawable.dot
        "wewe" -> R.drawable.wewe
        "omni" -> R.drawable.omni
        "pyth" -> R.drawable.pyth
        "snx" -> R.drawable.snx
        "sushi" -> R.drawable.sushi
        "yfi" -> R.drawable.yfi
        "mkr" -> R.drawable.mkr
        "pepe" -> R.drawable.pepe
        "reth" -> R.drawable.reth
        "shib" -> R.drawable.shib
        "w" -> R.drawable.w
        "aave" -> R.drawable.aave
        "bal" -> R.drawable.bal
        "bat" -> R.drawable.bat
        "bnb" -> R.drawable.bnb
        "busd" -> R.drawable.busd
        "comp" -> R.drawable.comp
        "grt" -> R.drawable.grt
        "mim" -> R.drawable.mim
        "ldo" -> R.drawable.ldo
        "dai" -> R.drawable.dai
        "knc" -> R.drawable.knc
        "aero" -> R.drawable.aero
        "bag" -> R.drawable.bag
        "blooks" -> R.drawable.blooks
        "cbeth" -> R.drawable.cbeth
        "coq" -> R.drawable.coq
        "dackie" -> R.drawable.dackie
        "ezeth" -> R.drawable.ezeth
        "joe" -> R.drawable.joe
        "juice" -> R.drawable.juice
        "om" -> R.drawable.om
        "png" -> R.drawable.png
        "savax" -> R.drawable.savax
        "usdb" -> R.drawable.usdb
        "weth" -> R.drawable.weth
        "zero" -> R.drawable.zero
        "zksync", "zsync-era" -> R.drawable.zksync
        "sui" -> R.drawable.sui
        "ton" -> R.drawable.ton
        "osmo" -> R.drawable.osmo
        "wif", "dogwifhat-wif-logo" -> R.drawable.wif
        "ray", "raydium-ray-seeklogo-2" -> R.drawable.ray
        "jupiter" -> R.drawable.jup
        "luna" -> R.drawable.luna
        "lunc" -> R.drawable.lunc
        "astro", "terra-astroport" -> R.drawable.astro
        "mnta" -> R.drawable.mnta
        "nstk" -> R.drawable.nstk
        "usk" -> R.drawable.usk
        "wink" -> R.drawable.wink
        "nami" -> R.drawable.nami
        "xrp" -> R.drawable.xrp
        "kween" -> R.drawable.kween
        "ion" -> R.drawable.ion
        "akash" -> R.drawable.akash
        "rkuji" -> R.drawable.rkuji
        "tron" -> R.drawable.tron
        "lvn", "levana" -> R.drawable.lvn
        "fuzion" -> R.drawable.fuzion
        "vult", "vulti" -> R.drawable.vulti
        "tcy" -> R.drawable.tcy
        "zec" -> R.drawable.zcash
        "ruji" -> R.drawable.ruji
        "yrune" -> R.drawable.yrune
        "ytcy" -> R.drawable.ytcy
        "mantle" -> R.drawable.mantle
        "stcy" -> R.drawable.stcy
        "auto" -> R.drawable.auto_token_kujira
        "sei" -> R.drawable.sei
        "hype", "whype" -> R.drawable.hyperliquid
        "khype" -> R.drawable.khype
        "ubtc" -> R.drawable.bitcoin
        "ufart" -> R.drawable.ufart
        "usdt0" -> R.drawable.usdt0
        "vhype" -> R.drawable.vhype
        "vkhype" -> R.drawable.vkhype
        "wsthype" -> R.drawable.wsthype
        else -> logoName
    }
}