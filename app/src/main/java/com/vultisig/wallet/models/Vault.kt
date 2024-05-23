package com.vultisig.wallet.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Vault(
    val id: String,
    var name: String,
    var pubKeyECDSA: String = "",
    var pubKeyEDDSA: String = "",
    var hexChainCode: String = "",
    var localPartyID: String = "",
    var signers: List<String> = listOf(),
    var resharePrefix: String = "",
    var keyshares: List<KeyShare> = listOf(),
    val coins: List<Coin> = emptyList(),
) : Parcelable
