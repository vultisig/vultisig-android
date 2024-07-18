package com.vultisig.wallet.models

internal data class ShareVaultQrModel(
    val uid: String,
    val name: String,
    val public_key_ecdsa: String,
    val public_key_eddsa: String,
    val hex_chain_code: String,
)