package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

internal data class BlowfishRequest(
    @SerializedName("userAccount")
    val userAccount: String,
    @SerializedName("metadata")
    val metadata: BlowfishMetadata,
    @SerializedName("txObjects")
    val txObjects: List<BlowfishTxObject>?,
    @SerializedName("simulatorConfig")
    val simulatorConfig: BlowfishSimulatorConfig?,
    @SerializedName("transactions")
    val transactions: List<String>?
)


internal data class BlowfishTxObject(
    @SerializedName("from")
    val from: String,
    @SerializedName("to")
    val to: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("data")
    val data: String?,
)

internal data class BlowfishMetadata(
    @SerializedName("origin")
    val origin: String,
)

internal data class BlowfishSimulatorConfig(
    @SerializedName("blockNumber")
    val blockNumber: String?,
    @SerializedName("blockTimestamp")
    val stateOverrides: BlowfishStateOverrides?,
)

internal data class BlowfishStateOverrides(
    @SerializedName("nativeBalances")
    val nativeBalances: List<BlowfishNativeBalance>,
    @SerializedName("storage")
    val storage: List<BlowfishStorage>?
)

internal data class BlowfishNativeBalance(
    val address: String,
    val value: String,
)

internal data class BlowfishStorage(
    val address: String,
    val slot: String,
    val value: String,
)

