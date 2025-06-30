//package com.vultisig.wallet.data.api.swapAggregators
//
//import io.ktor.client.HttpClient
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.Json
//import javax.inject.Inject
//
//interface kyberSawpApi1 {
//
//
//}
//
//class kyberApiImpl @Inject constructor(
//    private val httpClient: HttpClient,
//    private val json: Json,
//) : KyberApi {
//    private val baseUrl = "https://aggregator-api.kyberswap.com"
//    private val clientId = "vultisig-android"
//
//}
//
//@Serializable
//data class KyberSwapTransactionBuildingRespone(
//    @SerialName("slippageTolerance")
//    val slippageTolerance: Int,
//    @SerialName("sender")
//    val sender: String,
//    @SerialName("recipient")
//    val recipient: String,
//    @SerialName("skipSimulateTx")
//    val skipSimulateTx: Boolean,
//    @SerialName("source")
//    val source: String,
//    @SerialName("enableSlippageProtection")
//    val enableSlippageProtection: Boolean,
//    @SerialName("feeReceiver")
//    val feeReceiver: String,
//    @SerialName("feeAmount")
//    val feeAmount: String,
//    @SerialName("chargeFeeBy")
//    val chargeFeeBy: String,
//    @SerialName("isInBps")
//    val isInBps: Boolean
//)