package com.voltix.wallet.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
class Vault(var name: String) : Parcelable {
    var PubKeyECDSA: String = ""
    var PubKeyEDDSA: String = ""
    var CreatedAt: Date = Date()
    var HexChainCode: String = ""
    var LocalPartyID: String = ""
    var signers: List<String> = listOf()
    var ResharePrefix: String = ""
    var Keyshares: List<KeyShare> = listOf()
    var Coins: MutableList<Coin> = mutableListOf()

}

