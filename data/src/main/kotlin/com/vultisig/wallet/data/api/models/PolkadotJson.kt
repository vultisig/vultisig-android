package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class PolkadotResponseJson(
    @SerialName("code")
    val code: Int,
    @SerialName("data")
    val data: PolkadotDataJson,
)

@Serializable
data class PolkadotDataJson(
    @SerialName("account")
    val account: PolkadotAccountJson,
)

@Serializable
data class PolkadotAccountJson(
    @SerialName("balance")
    val balance: String,
)
