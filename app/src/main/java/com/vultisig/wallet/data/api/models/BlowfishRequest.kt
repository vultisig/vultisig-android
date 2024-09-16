package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BlowfishRequest(
    @SerialName("userAccount")
    val userAccount: String,
    @SerialName("metadata")
    val metadata: BlowfishMetadata,
    @SerialName("txObjects")
    val txObjects: List<BlowfishTxObject>?,
    @SerialName("simulatorConfig")
    val simulatorConfig: BlowfishSimulatorConfig?,
    @SerialName("transactions")
    val transactions: List<String>?
)


@Serializable
internal data class BlowfishTxObject(
    @SerialName("from")
    val from: String,
    @SerialName("to")
    val to: String,
    @SerialName("value")
    val value: String,
    @SerialName("data")
    val data: String?,
)

@Serializable
internal data class BlowfishMetadata(
    @SerialName("origin")
    val origin: String,
)

@Serializable
internal data class BlowfishSimulatorConfig(
    @SerialName("blockNumber")
    val blockNumber: String?,
    @SerialName("blockTimestamp")
    val stateOverrides: BlowfishStateOverrides?,
)

@Serializable
internal data class BlowfishStateOverrides(
    @SerialName("nativeBalances")
    val nativeBalances: List<BlowfishNativeBalance>,
    @SerialName("storage")
    val storage: List<BlowfishStorage>?
)

@Serializable
internal data class BlowfishNativeBalance(
    @SerialName("address")
    val address: String,
    @SerialName("value")
    val value: String,
)

@Serializable
internal data class BlowfishStorage(
    @SerialName("address")
    val address: String,
    @SerialName("slot")
    val slot: String,
    @SerialName("value")
    val value: String,
)

