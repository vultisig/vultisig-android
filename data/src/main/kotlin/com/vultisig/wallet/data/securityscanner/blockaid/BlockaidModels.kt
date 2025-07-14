package com.vultisig.wallet.data.securityscanner.blockaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolanaScanTransactionRequestJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("metadata")
    val metadata: CommonMetadataJson,
    @SerialName("options")
    val options: List<String>,
    @SerialName("account_address")
    val accountAddress: String,
    @SerialName("encoding")
    val encoding: String,
    @SerialName("transactions")
    val transactions: List<String>,
    @SerialName("method")
    val method: String,
)

@Serializable
data class SuiScanTransactionRequestJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("metadata")
    val metadata: CommonMetadataJson,
    @SerialName("options")
    val options: List<String>,
    @SerialName("account_address")
    val accountAddress: String,
    @SerialName("transaction")
    val transaction: String,
)

@Serializable
data class BitcoinScanTransactionRequestJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("metadata")
    val metadata: CommonMetadataJson,
    @SerialName("options")
    val options: List<String>,
    @SerialName("account_address")
    val accountAddress: String,
    @SerialName("transaction")
    val transaction: String,
)

@Serializable
data class EthereumScanTransactionRequestJson(
    @SerialName("chain")
    val chain: String,
    @SerialName("metadata")
    val metadata: MetadataJson,
    @SerialName("options")
    val options: List<String>,
    @SerialName("account_address")
    val accountAddress: String,
    @SerialName("data")
    val data: DataJson,
    @SerialName("simulate_with_estimated_gas")
    val simulatedWithEstimatedGas: Boolean = false,
) {
    @Serializable
    data class MetadataJson(
        @SerialName("domain")
        val domain: String,
    )

    @Serializable
    data class DataJson(
        @SerialName("from")
        val from: String,
        @SerialName("to")
        val to: String,
        @SerialName("data")
        val data: String,
        @SerialName("value")
        val value: String,
    )
}

@Serializable
data class CommonMetadataJson(
    @SerialName("type")
    val type: String = "wallet",
    @SerialName("url")
    val url: String,
)

@Serializable
data class BlockaidTransactionScanResponseJson(
    @SerialName("request_id")
    val requestId: String?,
    @SerialName("account_address")
    val accountAddress: String?,
    @SerialName("status")
    val status: String?,
    @SerialName("validation")
    val validation: BlockaidValidationJson?,
    @SerialName("result")
    val result: BlockaidSolanaResultJson?,
    @SerialName("error")
    val error: String?,
) {
    @Serializable
    data class BlockaidSolanaResultJson(
        @SerialName("validation")
        val validation: BlockaidSolanaValidationJson,
    ) {
        @Serializable
        data class BlockaidSolanaValidationJson(
            @SerialName("result_type")
            val resultType: String,
            @SerialName("reason")
            val reason: String,
            @SerialName("features")
            val features: List<String> = emptyList(),
            @SerialName("extended_features")
            val extendedFeatures: List<BlockaidSolanaExtendedFeaturesJson> = emptyList(),
        ) {
            @Serializable
            data class BlockaidSolanaExtendedFeaturesJson(
                @SerialName("type")
                val type: String,
                @SerialName("description")
                val description: String,
            )
        }
    }

    @Serializable
    data class BlockaidValidationJson(
        @SerialName("status")
        val status: String?,
        @SerialName("classification")
        val classification: String?,
        @SerialName("result_type")
        val resultType: String?,
        @SerialName("description")
        val description: String?,
        @SerialName("reason")
        val reason: String?,
        @SerialName("features")
        val features: List<BlockaidFeatureJson>?,
        @SerialName("error")
        val error: String?,
    ){
        @Serializable
        data class BlockaidFeatureJson(
            @SerialName("type")
            val type: String,
            @SerialName("feature_id")
            val featureId: String,
            @SerialName("description")
            val description: String,
            @SerialName("address")
            val address: String?,
        )
    }
}