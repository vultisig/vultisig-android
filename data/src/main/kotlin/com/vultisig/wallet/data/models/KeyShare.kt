package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

@Serializable
data class KeyShare(
    val pubKey: String,
    val keyShare: String,
)
