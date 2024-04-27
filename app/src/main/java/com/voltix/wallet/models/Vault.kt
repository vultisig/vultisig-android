package com.voltix.wallet.models

import android.os.Parcelable
import java.util.Date
import kotlinx.parcelize.Parcelize
@Parcelize
class Vault(var name: String) : Parcelable {
    var PubKeyECDSA: String = ""
    var PubKeyEDDSA: String = ""
    var CreatedAt: Date = Date()
    var HexChainCode:String = ""
    var LocalPartyID: String = ""
    var ResharePrefix:String = ""
    var Keyshares: MutableList<KeyShare> = mutableListOf()
    var Coins:MutableList<Coin> = mutableListOf()

}

