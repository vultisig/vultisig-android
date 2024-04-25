package com.voltix.wallet.models

import java.util.Date

class Vault(var name: String) {
    var PubKeyECDSA: String = ""
    var PubKeyEDDSA: String = ""
    var CreatedAt: Date = Date()
    var HexChainCode:String = ""
    var LocalPartyID: String = ""
    var ResharePrefix:String = ""
    var Keyshares: MutableList<KeyShare> = mutableListOf()
    var Coins:MutableList<Coin> = mutableListOf()

}

