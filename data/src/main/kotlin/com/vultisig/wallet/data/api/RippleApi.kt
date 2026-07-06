package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseJson
import RippleBroadcastSuccessResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber

interface RippleApi {
    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getBalance(coin: Coin): BigInteger

    suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountInfoResponseJson?

    suspend fun fetchServerState(): RippleServerStateResponseJson

    suspend fun getTsStatus(txHash: String): RippleBroadcastSuccessResponseJson?
}

internal class RippleApiImp @Inject constructor(private val http: HttpClient) : RippleApi {

    override suspend fun broadcastTransaction(tx: String): String {
        try {
            val payload =
                RpcPayload(
                    method = "submit",
                    params = buildJsonArray { addJsonObject { put("tx_blob", tx) } },
                )
            val response = http.post(BASE_XRP_CLUSTER) { setBody(payload) }

            val rpcResp = response.bodyOrThrow<RippleBroadcastResponseResponseJson>()

            val engineResult = rpcResp.result.engineResult
            val resultMessage = rpcResp.result.engineResultMessage
            val hash = rpcResp.result.txJson?.hash

            // A benign duplicate-broadcast race: the peer's identical transaction was already
            // applied/queued, so the transaction is (or will be) on-chain and we can recover its
            // hash rather than treat it as a failure.
            val alreadyApplied =
                resultMessage?.contains("The transaction was applied", ignoreCase = true) == true ||
                    resultMessage.equals(
                        "This sequence number has already passed.",
                        ignoreCase = true,
                    ) ||
                    resultMessage.equals("The transaction is redundant.", ignoreCase = true)

            if (engineResult == "tesSUCCESS" || alreadyApplied) {
                if (!hash.isNullOrBlank()) {
                    return hash
                }
                // Submitted but the node returned no hash. Don't invent one from the message; let
                // the caller's on-chain recovery confirm using the locally computed hash instead.
                error("XRP broadcast returned no transaction hash (engine_result=$engineResult)")
            }

            // Any other engine result is a genuine rejection (e.g. temBAD_FEE, tecUNFUNDED). Throw
            // the node's message instead of returning it as a fake txid, so the keysign surfaces
            // the
            // failure (matching iOS RippleService) rather than persisting the rejection text as a
            // hash.
            error(resultMessage ?: "XRP broadcast failed (engine_result=$engineResult)")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Error in Broadcast XRP Transaction")
            error(e.message ?: "Error in Broadcast XRP Transaction")
        }
    }

    override suspend fun getTsStatus(txHash: String): RippleBroadcastSuccessResponseJson? {
        val payload =
            RpcPayload(
                method = "tx",
                params =
                    buildJsonArray {
                        addJsonObject {
                            put("transaction", txHash)
                            put("binary", false)
                            put("api_version", 2)
                        }
                    },
            )

        val response = http.post(BASE_XRP_CLUSTER) { setBody(payload) }

        return response.bodyOrThrow<RippleBroadcastSuccessResponseJson>()
    }

    override suspend fun getBalance(coin: Coin): BigInteger = supervisorScope {
        try {
            val accountInfoDeferred = async { fetchAccountsInfo(coin.address) }
            val reservedBalanceDeferred = async { fetchServerState() }

            val accountInfo = accountInfoDeferred.await()
            val reservedBalance = reservedBalanceDeferred.await()

            val balance = accountInfo?.getBalance() ?: BigInteger.ZERO
            val ownerCount = accountInfo?.getOwnerCount() ?: BigInteger.ZERO
            val accountReservedBalance =
                reservedBalance.getBaseReserve() + (ownerCount * reservedBalance.getIncReserve())

            maxOf(balance - accountReservedBalance, BigInteger.ZERO)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error in getBalance: ${e.message}")
            BigInteger.ZERO
        }
    }

    override suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountInfoResponseJson? {
        return try {
            val payload =
                RpcPayload(
                    method = "account_info",
                    params =
                        buildJsonArray {
                            addJsonObject {
                                put("account", walletAddress)
                                put("ledger_index", "current")
                                put("queue", true)
                            }
                        },
                )
            val response = http.post(BASE_XRP_CLUSTER) { setBody(payload) }
            response.bodyOrThrow<RippleAccountInfoResponseJson>()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error in fetchTokenAccountsByOwner: ${e.message}")
            error(e.message ?: "Error in fetchTokenAccountsByOwner")
        }
    }

    override suspend fun fetchServerState(): RippleServerStateResponseJson {
        val payload = RpcPayload(method = "server_state", params = buildJsonArray {})

        return http
            .post(BASE_XRP_CLUSTER) { setBody(payload) }
            .bodyOrThrow<RippleServerStateResponseJson>()
    }

    private companion object {
        const val BASE_XRP_VULTISIG: String = "https://api.vultisig.com/ripple"
        const val BASE_XRP_CLUSTER: String = "https://xrplcluster.com"
    }
}

internal fun RippleAccountInfoResponseJson.getBalance(): BigInteger =
    this.result?.accountData?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO

internal fun RippleAccountInfoResponseJson.getOwnerCount(): BigInteger =
    this.result?.accountData?.ownerCount?.toBigInteger() ?: BigInteger.ZERO

internal fun RippleServerStateResponseJson.getBaseReserve(): BigInteger =
    this.result?.state?.validateLedger?.reservedBase?.toBigInteger() ?: BigInteger.ZERO

internal fun RippleServerStateResponseJson.getIncReserve(): BigInteger =
    this.result?.state?.validateLedger?.reserveInc?.toBigInteger() ?: BigInteger.ZERO

@Serializable
data class RippleAccountInfoResponseJson(
    @SerialName("result") val result: RippleAccountInfoResponseResultJson? = null
)

@Serializable
data class RippleAccountInfoResponseResultJson(
    @SerialName("account_data") val accountData: RippleAccountInfoResponseAccountDataJson? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("validated") val validated: Boolean? = null,
    @SerialName("ledger_current_index") val ledgerCurrentIndex: Int? = null,
)

@Serializable
data class RippleAccountInfoResponseAccountDataJson(
    @SerialName("Balance") val balance: String? = null, // Total user balance = available + reserved
    @SerialName("Sequence") val sequence: Int? = null,
    @SerialName("OwnerCount") val ownerCount: Int = 0,
)

@Serializable
data class RippleServerStateResponseJson(
    @SerialName("result") val result: RippleServerStateResultJson?
)

@Serializable
data class RippleServerStateResultJson(@SerialName("state") val state: RippleStateJson) {
    @Serializable
    data class RippleStateJson(
        @SerialName("validated_ledger") val validateLedger: RippleValidateLedger,
        @SerialName("load_base") val loadBase: Long,
        @SerialName("load_factor") val loadFactor: Long,
    ) {
        @Serializable
        data class RippleValidateLedger(
            @SerialName("reserve_base") val reservedBase: Long,
            @SerialName("reserve_inc") val reserveInc: Long,
            @SerialName("base_fee") val baseFee: Long,
        )
    }
}
