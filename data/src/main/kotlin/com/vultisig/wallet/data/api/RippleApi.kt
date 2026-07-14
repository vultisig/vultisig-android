package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseJson
import RippleBroadcastResponseResponseResultJson
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
        val result =
            try {
                val payload =
                    RpcPayload(
                        method = "submit",
                        params = buildJsonArray { addJsonObject { put("tx_blob", tx) } },
                    )
                http
                    .post(BASE_XRP_CLUSTER) { setBody(payload) }
                    .bodyOrThrow<RippleBroadcastResponseResponseJson>()
                    .result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Error in Broadcast XRP Transaction")
                error(e.message ?: "Error in Broadcast XRP Transaction")
            }

        return try {
            resolveBroadcastHash(result)
        } catch (e: RippleBroadcastException) {
            Timber.e(e, "Error in Broadcast XRP Transaction")
            throw e
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

    // A network failure (timeout / no connectivity) must propagate so the balance layer can keep
    // the last-known value or surface a loading/error state — never swallow it into ZERO, which is
    // indistinguishable from a genuinely empty account and reads as "the funds disappeared". An
    // unfunded account returns an HTTP 200 with a null `account_data` (actNotFound), so it still
    // resolves to zero here without throwing.
    override suspend fun getBalance(coin: Coin): BigInteger = supervisorScope {
        val accountInfoDeferred = async { fetchAccountsInfo(coin.address) }
        val reservedBalanceDeferred = async { fetchServerState() }

        val accountInfo = accountInfoDeferred.await()
        val reservedBalance = reservedBalanceDeferred.await()

        val balance = accountInfo?.getBalance() ?: BigInteger.ZERO
        val ownerCount = accountInfo?.getOwnerCount() ?: BigInteger.ZERO
        val accountReservedBalance =
            reservedBalance.getBaseReserve() + (ownerCount * reservedBalance.getIncReserve())

        maxOf(balance - accountReservedBalance, BigInteger.ZERO)
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
            Timber.e(e, "Error in fetchAccountsInfo")
            // Rethrow the original exception so its transport cause (timeout / no connectivity) is
            // preserved for classification instead of being flattened into a generic error.
            throw e
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

private const val RIPPLE_ENGINE_RESULT_SUCCESS = "tesSUCCESS"

/**
 * Resolves an XRPL `submit` engine result into a trackable transaction hash, or throws a typed
 * [RippleBroadcastException] carrying the real failure reason.
 *
 * XRPL echoes the deterministic `tx_json.hash` back regardless of the engine result. We return that
 * hash for a successful submit (`tesSUCCESS`) and for a benign duplicate-broadcast race
 * (`tefPAST_SEQ` / `tefALREADY`, or a node that only reports an "already applied/redundant"
 * message) so on-chain recovery can verify it by hash without re-broadcasting. Every other engine
 * result (`tem*`, `tec*`, other `tef*`, …) is a genuine rejection: we surface the engine code +
 * message as an exception and never persist the engine message as a fake transaction id.
 */
internal fun resolveBroadcastHash(result: RippleBroadcastResponseResponseResultJson): String {
    val trackable =
        result.engineResult == RIPPLE_ENGINE_RESULT_SUCCESS ||
            isDuplicateBroadcast(result.engineResult, result.engineResultMessage)

    val hash = result.txJson?.hash
    if (trackable && !hash.isNullOrBlank()) {
        return hash
    }
    throw RippleBroadcastException(result.engineResult, result.engineResultMessage)
}

private fun isDuplicateBroadcast(engineResult: String, message: String?): Boolean {
    if (engineResult == "tefPAST_SEQ" || engineResult == "tefALREADY") {
        return true
    }
    // Fall back to the human-readable message for nodes/proxies that don't echo the engine code.
    return message?.contains("The transaction was applied", ignoreCase = true) == true ||
        message.equals("This sequence number has already passed.", ignoreCase = true) ||
        message.equals("The transaction is redundant.", ignoreCase = true)
}

/**
 * A genuine XRPL broadcast rejection. Carries the engine result code and message so the keysign
 * flow surfaces the real reason instead of persisting a garbage transaction hash.
 */
internal class RippleBroadcastException(
    val engineResult: String?,
    val engineResultMessage: String?,
) : Exception(buildRippleBroadcastMessage(engineResult, engineResultMessage))

private fun buildRippleBroadcastMessage(code: String?, message: String?): String {
    val resolvedCode = code?.takeIf { it.isNotBlank() } ?: "unknown"
    return if (!message.isNullOrBlank()) {
        "Ripple broadcast failed ($resolvedCode): $message"
    } else {
        "Ripple broadcast failed ($resolvedCode)"
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
    @SerialName("Flags") val flags: Long? = null,
)

// lsfRequireDestTag: set when an account rejects any payment that arrives without a destination
// tag.
private const val LSF_REQUIRE_DEST_TAG = 0x00020000L

/** True when the destination account has the RequireDestinationTag flag set. */
fun RippleAccountInfoResponseJson.requiresDestinationTag(): Boolean =
    ((this.result?.accountData?.flags ?: 0L) and LSF_REQUIRE_DEST_TAG) != 0L

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
