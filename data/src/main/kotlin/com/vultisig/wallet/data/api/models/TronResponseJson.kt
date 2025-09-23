package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
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
data class TronChainParametersJson(
    val chainParameter: List<TronChainParameterJson>,
) {
    private val chainParameterMapped = chainParameter.associate { it.key to it.value }

    val memoFeeEstimate: Long
        get() = chainParameterMapped["getMemoFee"] ?: 0L

    val createAccountFeeEstimate: Long
        get() = chainParameterMapped["getCreateAccountFee"] ?: 0L

    val createNewAccountFeeEstimateContract: Long
        get() = chainParameterMapped["getCreateNewAccountFeeInSystemContract"] ?: 0L

    val energyFee: Long
        get() = chainParameterMapped["getEnergyFee"] ?: 0L

    // Atm according to network: 1 bandwidth -> 1000 SUN
    val bandwidthFeePrice: Long
        get() = chainParameterMapped["getTransactionFee"] ?: 0L
}

@Serializable
data class TronChainParameterJson(
    val key: String,
    val value: Long = 0L,
)

@Serializable
internal data class TronAccountRequestJson(
    val address: String,
    val visible: Boolean,
)

@Serializable
internal data class TronContractRequestJson(
    val value: String,
) {
    @EncodeDefault
    val visible: Boolean = true
}

@Serializable
data class TronAccountResourceJson(
    @SerialName("freeNetUsed")
    val freeNetUsed: Long = 0L,
    @SerialName("freeNetLimit")
    val freeNetLimit: Long = 0L,
    @SerialName("NetUsed")
    val netUsed: Long = 0L,
    @SerialName("NetLimit")
    val netLimit: Long = 0L,
    @SerialName("EnergyLimit")
    val energyLimit: Long = 0L,
    @SerialName("EnergyUsed")
    val energyUsed: Long = 0L,
    @SerialName("TotalNetLimit")
    val totalNetLimit: Long = 0L,
    @SerialName("TotalNetWeight")
    val totalNetWeight: Long = 0L,
    @SerialName("TotalEnergyLimit")
    val totalEnergyLimit: Long = 0L,
    @SerialName("TotalEnergyWeight")
    val totalEnergyWeight: Long = 0L,
    @SerialName("tronPowerUsed")
    val tronPowerUsed: Long = 0L,
    @SerialName("tronPowerLimit")
    val tronPowerLimit: Long = 0L,
)

@Serializable
data class TronAccountJson(
    @SerialName("address")
    val address: String = "",
)

@Serializable
data class TronContractInfoJson(
    @SerialName("contract_state")
    val contractState: ContractStateJson,
) {
    @Serializable
    data class ContractStateJson(
        @SerialName("energy_factor")
        val energyFactor: String = "0",
    )
}