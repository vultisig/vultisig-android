package com.vultisig.wallet.data.api

import RippleBroadcastResponseResponseJson
import RippleBroadcastResponseResponseResultJson
import RippleBroadcastSuccessResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.RippleTokenIdentity
import com.vultisig.wallet.data.models.rippleTokenIdentity
import com.vultisig.wallet.data.models.toRippleTokenUnits
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber

interface RippleApi {
    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getBalance(coin: Coin): BigInteger

    /**
     * Balance of a single issued-currency trust line, scaled to [RIPPLE_TOKEN_DECIMALS]. Returns
     * zero when the account holds no line for [coin]'s currency/issuer pair.
     */
    suspend fun getTokenBalance(coin: Coin): BigInteger

    /**
     * Every trust line the account holds, following `account_lines` pagination markers. Callers get
     * both sides' view: a negative [RippleTrustLineJson.balance] means the account owes the
     * counterparty, so only positive lines represent spendable holdings.
     */
    suspend fun fetchAccountLines(walletAddress: String): List<RippleTrustLineJson>

    suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountInfoResponseJson?

    suspend fun fetchServerState(): RippleServerStateResponseJson

    suspend fun getTsStatus(txHash: String): RippleBroadcastSuccessResponseJson?
}

/**
 * Coalesces `account_lines` reads. One response carries every trust line for an address, yet the
 * balance layer asks per token — each call picking one line and discarding the rest — so a vault
 * holding N issued currencies would fire N identical (and possibly multi-page) request chains at
 * the public XRPL cluster. A per-address [Mutex] shares concurrent in-flight reads and a short TTL
 * lets a sequential fan-out reuse the first response, while a refresh past the window still hits
 * the network. Mirrors [CosmosBalanceCache], which solves the same fan-out on the Cosmos bank
 * endpoint.
 */
internal class RippleAccountLinesCache(private val ttlMs: Long = DEFAULT_TTL_MS) {

    private class Entry(val value: List<RippleTrustLineJson>, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun getOrFetch(
        address: String,
        fetch: suspend () -> List<RippleTrustLineJson>,
    ): List<RippleTrustLineJson> =
        locks
            .computeIfAbsent(address) { Mutex() }
            .withLock {
                val now = System.currentTimeMillis()
                entries[address]?.takeIf { now < it.expiresAt }?.value
                    ?: fetch().also { entries[address] = Entry(it, now + ttlMs) }
            }

    companion object {
        // Long enough to absorb the per-token fan-out for one address, short enough that a later
        // manual refresh still fetches fresh balances.
        private const val DEFAULT_TTL_MS = 10_000L
    }
}

internal class RippleApiImp @Inject constructor(private val http: HttpClient) : RippleApi {

    // Safe to hold per instance: RippleApi is bound as a @Singleton, so every caller shares one
    // cache. See RippleAccountLinesCache.
    private val accountLinesCache = RippleAccountLinesCache()

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

        val body = response.bodyOrThrow<RippleBroadcastSuccessResponseJson>()

        // A signed XRPL tx that never reached a validating node before its LastLedgerSequence, or
        // one still propagating, returns the `txnNotFound` error response (error_code 29). That is
        // a clean "not on-chain yet" signal — not a failure — so report it as pending (null) rather
        // than letting the strict deserializer throw on every poll.
        if (
            body.result.error == RIPPLE_TXN_NOT_FOUND ||
                body.result.errorCode == RIPPLE_TXN_NOT_FOUND_CODE
        ) {
            return null
        }

        return body
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

    override suspend fun getTokenBalance(coin: Coin): BigInteger {
        val identity = coin.rippleTokenIdentity() ?: return BigInteger.ZERO
        val line =
            fetchAccountLines(coin.address).firstOrNull { it.matches(identity) }
                ?: return BigInteger.ZERO
        return line.balance.toRippleTokenUnits()
    }

    override suspend fun fetchAccountLines(walletAddress: String): List<RippleTrustLineJson> =
        accountLinesCache.getOrFetch(walletAddress) { fetchAllAccountLinePages(walletAddress) }

    private suspend fun fetchAllAccountLinePages(walletAddress: String): List<RippleTrustLineJson> {
        val lines = mutableListOf<RippleTrustLineJson>()
        var marker: JsonElement? = null

        // XRPL caps a single account_lines response at `limit` entries and hands back an opaque
        // marker to resume from. The page cap bounds the walk so a node that keeps echoing the
        // same marker can't spin here forever.
        repeat(MAX_ACCOUNT_LINES_PAGES) {
            val payload =
                RpcPayload(
                    method = "account_lines",
                    params =
                        buildJsonArray {
                            addJsonObject {
                                put("account", walletAddress)
                                put("ledger_index", "validated")
                                put("limit", ACCOUNT_LINES_PAGE_SIZE)
                                marker?.let { put("marker", it) }
                            }
                        },
                )

            val result =
                http
                    .post(BASE_XRP_CLUSTER) { setBody(payload) }
                    .bodyOrThrow<RippleAccountLinesResponseJson>()
                    .result

            // An unfunded account answers with `actNotFound` and no `lines` — a legitimately empty
            // holding, not a failure, so it falls through to an empty list.
            result?.lines?.let(lines::addAll)

            val next = result?.marker ?: return lines
            if (next == marker) return lines
            marker = next
        }

        Timber.w("account_lines paging cap reached for %s", walletAddress)
        return lines
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
        const val RIPPLE_TXN_NOT_FOUND: String = "txnNotFound"
        const val RIPPLE_TXN_NOT_FOUND_CODE: Int = 29
        const val ACCOUNT_LINES_PAGE_SIZE: Int = 400
        const val MAX_ACCOUNT_LINES_PAGES: Int = 25
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
data class RippleAccountLinesResponseJson(
    @SerialName("result") val result: RippleAccountLinesResultJson? = null
)

@Serializable
data class RippleAccountLinesResultJson(
    @SerialName("lines") val lines: List<RippleTrustLineJson>? = null,
    @SerialName("marker") val marker: JsonElement? = null,
    @SerialName("error") val error: String? = null,
)

/**
 * One entry of an `account_lines` response — a trust line between the queried account and
 * [account], the counterparty that issues [currency].
 *
 * [balance] is a decimal string from the queried account's perspective, so it is negative when the
 * account is the issuing side of the line. [currency] is either a 3-character ASCII code or the
 * 40-character hex form of a 160-bit code; see [rippleCurrencyTicker] for the display form.
 */
@Serializable
data class RippleTrustLineJson(
    @SerialName("account") val account: String,
    @SerialName("currency") val currency: String,
    @SerialName("balance") val balance: String,
    @SerialName("limit") val limit: String? = null,
    @SerialName("no_ripple") val noRipple: Boolean? = null,
    @SerialName("freeze") val freeze: Boolean? = null,
)

/**
 * Exact-match on the trust line's currency/issuer pair. Both halves are case-sensitive on XRPL —
 * `USD` and `usd` are distinct currencies, and issuer addresses are base58 — so neither is folded.
 */
internal fun RippleTrustLineJson.matches(identity: RippleTokenIdentity): Boolean =
    currency == identity.currency && account == identity.issuer

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
