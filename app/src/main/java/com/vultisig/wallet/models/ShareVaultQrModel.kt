package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class ShareVaultQrModel(
    @SerializedName("uid")
    val uid: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("public_key_ecdsa")
    val publicKeyEcdsa: String,
    @SerializedName("public_key_eddsa")
    val publicKeyEddsa: String,
    @SerializedName("hex_chain_code")
    val hexChainCode: String,
)