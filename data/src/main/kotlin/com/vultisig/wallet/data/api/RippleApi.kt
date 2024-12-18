package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject


interface RippleApi {
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountInfoResponseJson?
}

internal class RippleApiImp @Inject constructor(
    private val http: HttpClient,
) : RippleApi {
    private val rpcUrl: String = "https://api.vultisig.com/ripple"
    private val rpcUrl2: String = "https://xrplcluster.com"


    override suspend fun broadcastTransaction(hex: String): String {
        try {
            val payload = RpcPayload(
                method = "submit",
                params = buildJsonArray {
                    addJsonObject {
                        put(
                            "tx_blob",
                            hex
                        )
                    }
                }
            )
            val response = http.post(rpcUrl2) {
                setBody(payload)
            }

            val rpcResp = response.body<RippleBroadcastResponseResponseJson>()

            if (rpcResp.result.engineResult != "tesSUCCESS") {
                if (rpcResp.result.engineResultMessage.equals(
                        "This sequence number has already passed",
                        ignoreCase = true
                    )
                ) {
                    if (rpcResp.result.txJson?.hash != null) {
                        return rpcResp.result.txJson.hash
                    }
                }
                return rpcResp.result.engineResultMessage ?: ""
            }
            if (rpcResp.result.txJson?.hash?.isNotEmpty() == true) {
                return rpcResp.result.engineResultMessage ?: ""
            }
            return ""
        } catch (e: Exception) {
            Timber.e(
                "Error in Broadcast XRP Transaction",
                e.message
            )
            error(e.message ?: "Error in Broadcast XRP Transaction")
        }
    }


    override suspend fun getBalance(coin: Coin): BigInteger {
        try {
            val accountInfo = fetchAccountsInfo(coin.address)
            return accountInfo?.result?.accountData?.balance?.toBigInteger() ?: BigInteger.ZERO
        } catch (e: Exception) {
            Timber.e("Error in getBalance: ${e.message}")
            return BigInteger.ZERO
        }
    }

    override suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountInfoResponseJson? {
        return try {
            val payload = RpcPayload(
                method = "account_info",
                params = buildJsonArray {
                    addJsonObject {
                        put(
                            "account",
                            walletAddress
                        )
                        put(
                            "ledger_index",
                            "current"
                        )
                        put(
                            "queue",
                            true
                        )
                    }
                }
            )
            val response = http.post(rpcUrl2) {
                setBody(payload)
            }
            response.body<RippleAccountInfoResponseJson>()
        } catch (e: Exception) {
            Timber.e("Error in fetchTokenAccountsByOwner: ${e.message}")
           error(e.message ?: "Error in fetchTokenAccountsByOwner")
        }
    }
}


@Serializable
data class RippleAccountInfoResponseJson(
    @SerialName("result")
    val result: RippleAccountInfoResponseResultJson? = null,
)

@Serializable
data class RippleAccountInfoResponseResultJson(
    @SerialName("account_data")
    val accountData: RippleAccountInfoResponseAccountDataJson? = null,
    val status: String? = null,
    val validated: Boolean? = null
)

@Serializable
data class RippleAccountInfoResponseAccountDataJson(
    @SerialName("Balance")
    val balance: String? = null,
    @SerialName("Sequence")
    val sequence: Int? = null,
)


