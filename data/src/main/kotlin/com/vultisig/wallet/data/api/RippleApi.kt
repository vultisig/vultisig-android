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
    suspend fun fetchServerState(): RippleServerStateResponseJson
}

internal class RippleApiImp @Inject constructor(
    private val http: HttpClient,
) : RippleApi {

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
            val response = http.post(BASE_XRP_CLUSTER) {
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
            val accountInfoDeferred =
                async { fetchAccountsInfo(coin.address) }
            val reservedBalanceDeferred =
                async { fetchServerState() }

            val accountInfo = accountInfoDeferred.await()
            val reservedBalance = reservedBalanceDeferred.await()

            val balance = accountInfo?.getBalance() ?: BigInteger.ZERO
            val ownerCount = accountInfo?.getOwnerCount() ?: BigInteger.ZERO
            val accountReservedBalance =
                reservedBalance.getBaseReserve() + (ownerCount * reservedBalance.getIncReserve())

            maxOf(balance - accountReservedBalance, BigInteger.ZERO)
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
            val response = http.post(BASE_XRP_CLUSTER) {
                setBody(payload)
            }
            response.body<RippleAccountInfoResponseJson>()
        } catch (e: Exception) {
            Timber.e("Error in fetchTokenAccountsByOwner: ${e.message}")
            error(e.message ?: "Error in fetchTokenAccountsByOwner")
        }
    }

    override suspend fun fetchServerState(): RippleServerStateResponseJson {
        return try {
            val payload = RpcPayload(
                method = "server_state",
                params = buildJsonArray { }
            )

            return http.post(BASE_XRP_CLUSTER) {
                setBody(payload)
            }.bodyOrThrow<RippleServerStateResponseJson>()
        } catch (t: Throwable) {
            getDefaultRippleStateServer()
        }
    }

    // Returning these default values is acceptable if the RPC call fails. Reserve balances change
    // infrequently, and fetching them (even if it sometimes fails) is preferable to hardcoding them
    private fun getDefaultRippleStateServer() = RippleServerStateResponseJson(
        result = RippleServerStateResultJson(
            state = RippleServerStateResultJson.RippleStateJson(
                validateLedger = RippleServerStateResultJson.RippleStateJson.RippleValidateLedger(
                    reservedBase = 1000000,
                    reserveInc = 200000,
                )
            )
        )
    )

    private companion object {
        const val BASE_XRP_VULTISIG: String = "https://api.vultisig.com/ripple"
        const val BASE_XRP_CLUSTER: String = "https://xrplcluster.com"
    }
}

private fun RippleAccountInfoResponseJson.getBalance(): BigInteger =
    this.result?.accountData?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO

private fun RippleAccountInfoResponseJson.getOwnerCount(): BigInteger =
    this.result?.accountData?.ownerCount?.toBigInteger() ?: BigInteger.ZERO

private fun RippleServerStateResponseJson.getBaseReserve(): BigInteger =
    this.result?.state?.validateLedger?.reservedBase?.toBigInteger() ?: BigInteger.ZERO

private fun RippleServerStateResponseJson.getIncReserve(): BigInteger =
    this.result?.state?.validateLedger?.reserveInc?.toBigInteger() ?: BigInteger.ZERO


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
    val ownerCount: Int = 0,
)

@Serializable
data class RippleServerStateResponseJson(
    @SerialName("result")
    val result: RippleServerStateResultJson?
)

@Serializable
data class RippleServerStateResultJson(
    @SerialName("state")
    val state: RippleStateJson
) {
    @Serializable
    data class RippleStateJson(
        @SerialName("validated_ledger")
        val validateLedger: RippleValidateLedger,
        @SerialName("load_base")
        val loadBase: Long,
        @SerialName("load_factor")
        val loadFactor: Long,
    ) {
        @Serializable
        data class RippleValidateLedger(
            @SerialName("reserve_base")
            val reservedBase: Long,
            @SerialName("reserve_inc")
            val reserveInc: Long,
            @SerialName("base_fee")
            val baseFee: Long
        )
    }
}
