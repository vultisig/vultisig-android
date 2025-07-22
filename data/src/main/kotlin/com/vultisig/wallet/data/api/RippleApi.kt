package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
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
    suspend fun serverState(): RippleServerStateResponseJson
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

            val resultMessage = rpcResp.result.engineResultMessage

            if (rpcResp.result.engineResult != "tesSUCCESS") {
                if (
                    resultMessage?.contains(
                        "The transaction was applied", ignoreCase = true
                    ) == true ||
                    resultMessage.equals(
                        "This sequence number has already passed.",
                        ignoreCase = true
                    )
                ) {
                    if (rpcResp.result.txJson?.hash != null) {
                        return rpcResp.result.txJson.hash
                    }
                }
                return resultMessage ?: ""
            } else {
                val hash = rpcResp.result.txJson?.hash
                return if (hash?.isNotEmpty() == true) {
                    hash
                } else {
                    resultMessage ?: ""
                }
            }
        } catch (e: Exception) {
            Timber.e(
                e.message,
                "Error in Broadcast XRP Transaction",
            )
            error(e.message ?: "Error in Broadcast XRP Transaction")
        }
    }


    override suspend fun getBalance(coin: Coin): BigInteger = supervisorScope {
        try {
            val accountInfoDeferred = async { fetchAccountsInfo("rhhh49pFH96roGyuC4E5P4CHaNjS1k8gzM") }
            val reservedBalanceDeferred = async { serverState() }
            val balance =
                accountInfoDeferred.await()?.result?.accountData?.balance?.toBigInteger()
                    ?: BigInteger.ZERO
            val reservedBalance =
                reservedBalanceDeferred.await().result?.state?.validateLedger?.reservedBase?.toBigInteger()
                    ?: BigInteger.ZERO
            maxOf(balance - reservedBalance, BigInteger.ZERO)
        } catch (e: Exception) {
            Timber.e("Error in getBalance: ${e.message}")
            BigInteger.ZERO
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

    override suspend fun serverState(): RippleServerStateResponseJson {
        val payload = RpcPayload(
            method = "server_state",
            params = buildJsonArray { }
        )

        return http.post(rpcUrl2) {
            setBody(payload)
        }.bodyOrThrow<RippleServerStateResponseJson>()
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
    @SerialName("status")
    val status: String? = null,
    @SerialName("validated")
    val validated: Boolean? = null,
    @SerialName("ledger_current_index")
    val ledgerCurrentIndex: Int? = null,
)

@Serializable
data class RippleAccountInfoResponseAccountDataJson(
    @SerialName("Balance")
    val balance: String? = null, // Total user balance = available + reserved
    @SerialName("Sequence")
    val sequence: Int? = null,
    @SerialName("OwnerCount")
    val ownerCount: Int? = null,
)

@Serializable
data class RippleServerStateResponseJson(
    @SerialName("result")
    val result: RippleServerStateResultJson?
)

@Serializable
data class RippleServerStateResultJson(
    val state: RippleStateJson
) {
    @Serializable
    data class RippleStateJson(
        @SerialName("validated_ledger")
        val validateLedger: RippleValidateLedger,
    ) {
        @Serializable
        data class RippleValidateLedger(
            @SerialName("reserve_base")
            val reservedBase: String,
            @SerialName("reserve_inc")
            val reserveInc: String
        )
    }
}
