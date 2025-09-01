package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
internal data class TronBroadcastTxResponseJson(
    @SerialName("txid")
    val txId: String?,
    @SerialName("code")
    val code: String?
)

@Serializable
data class TronSpecificBlockJson(
    @SerialName("block_header")
    val blockHeader: TronSpecificBlockHeaderJson
)

@Serializable
data class TronSpecificBlockHeaderJson(
    @SerialName("raw_data")
    val rawData: TronSpecificBlockRawDataJson,
)

@Serializable
data class TronSpecificBlockRawDataJson(
    @SerialName("number")
    val number: ULong,
    @SerialName("txTrieRoot")
    val txTrieRoot: String,
    @SerialName("witness_address")
    val witnessAddress: String,
    @SerialName("parentHash")
    val parentHash: String,
    @SerialName("version")
    val version: ULong,
    @SerialName("timestamp")
    val timeStamp: ULong,
)

@Serializable
internal data class TronBalanceResponseJson(
    @SerialName("data")
    val tronBalanceResponseData: Array<TronBalanceResponseData>
)

@Serializable
internal data class TronBalanceResponseData(
    @SerialName("balance")
    @Contextual
    val balance: BigInteger,
    @SerialName("trc20")
    val trc20: Array<Map<String, @Contextual BigInteger>>
)

@Serializable
internal data class TronTriggerConstantContractJson(
    @SerialName("energy_used")
    val energyUsed: Long,
    @SerialName("energy_penalty")
    val energyPenalty: Long
)

@Serializable
data class TronChainParameters(
    val chainParameter: List<TronChainParameter>,
) {
    private val chainParameterMapped = chainParameter.associate { it.key to it.value }

    val memoFeeEstimate: Long
        get() = chainParameterMapped["getMemoFee"] ?: 0L
}

@Serializable
data class TronChainParameter(
    val key: String,
    val value: Long = 0L,
)