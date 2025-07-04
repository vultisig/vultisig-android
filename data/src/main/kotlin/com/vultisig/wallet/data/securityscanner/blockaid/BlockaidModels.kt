package com.vultisig.wallet.data.securityscanner.blockaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolanaScanTransactionRequest(
    val chain: String,
    val metadata: CommonMetadata,
    val options: List<String>,
    val accountAddress: String,
    val encoding: String,
    val transactions: List<String>
)

@Serializable
data class SuiScanTransactionRequest(
    val chain: String,
    val metadata: CommonMetadata,
    val options: List<String>,
    val accountAddress: String,
    val transaction: String,
)

@Serializable
data class BitcoinScanTransactionRequest(
    val chain: String,
    val metadata: CommonMetadata,
    val options: List<String>,
    val accountAddress: String,
    val transaction: String,
)

@Serializable
data class EthereumScanTransactionRequest(
    val chain: String,
    val metadata: Metadata,
    val options: List<String>,
    val accountAddress: String,
    val data: Data,
    @SerialName("simulate_with_estimated_gas")
    val simulatedWithEstimatedGas: Boolean = false,
) {
    @Serializable
    data class Metadata(
        val domain: String,
    )

    @Serializable
    data class Data(
        val from: String,
        val to: String,
        val data: String,
        val value: String,
    )
}

@Serializable
data class CommonMetadata(
    val type: String = "wallet",
    val url: String,
)

@Serializable
data class BlockaidTransactionScanResponse(
    @SerialName("request_id")
    val requestId: String?,
    @SerialName("account_address")
    val accountAddress: String?,
    val status: String?,
    val validation: BlockaidValidation?,
) {
    @Serializable
    data class BlockaidValidation(
        val status: String?,
        val classification: String?,
        @SerialName("result_type")
        val resultType: String?,
        val description: String?,
        val reason: String?,
        val features: List<BlockaidFeature>?
    ){
        @Serializable
        data class BlockaidFeature(
            val type: String,
            val severity: String?,
            val description: String,
            val address: String?,
        )
    }
}