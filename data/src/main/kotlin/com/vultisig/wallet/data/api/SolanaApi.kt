package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.JupiterApiImpl.Companion.JUPITER_URL
import com.vultisig.wallet.data.api.models.BroadcastTransactionRespJson
import com.vultisig.wallet.data.api.models.JupiterTokenResponseJson
import com.vultisig.wallet.data.api.models.RecentBlockHashResponseJson
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.api.models.SPLTokenRequestJson
import com.vultisig.wallet.data.api.models.SolanaAccountInfoResponseJson
import com.vultisig.wallet.data.api.models.SolanaBalanceJson
import com.vultisig.wallet.data.api.models.SolanaEpochInfoResponseJson
import com.vultisig.wallet.data.api.models.SolanaEpochInfoResultJson
import com.vultisig.wallet.data.api.models.SolanaFeeForMessageResponse
import com.vultisig.wallet.data.api.models.SolanaFeeObjectRespJson
import com.vultisig.wallet.data.api.models.SolanaMinimumBalanceForRentExemptionJson
import com.vultisig.wallet.data.api.models.SolanaProgramAccountJson
import com.vultisig.wallet.data.api.models.SolanaProgramAccountsResponseJson
import com.vultisig.wallet.data.api.models.SolanaRpcResponseJson
import com.vultisig.wallet.data.api.models.SolanaSignatureStatusesResult
import com.vultisig.wallet.data.api.models.SolanaStakeAccountDataJson
import com.vultisig.wallet.data.api.models.SolanaStakeAccountInfoResponseJson
import com.vultisig.wallet.data.api.models.SolanaVoteAccountsResponseJson
import com.vultisig.wallet.data.api.models.SolanaVoteAccountsResultJson
import com.vultisig.wallet.data.api.models.SplAmountRpcResponseJson
import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.api.models.SplResponseJson
import com.vultisig.wallet.data.api.models.SplTokenInfo
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.utils.postRpc
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingConfig
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.models.SplTokenDeserialized
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import wallet.core.jni.SolanaAddress

interface SolanaApi {
    suspend fun getBalance(address: String): BigInteger

    suspend fun getMinimumBalanceForRentExemption(): BigInteger

    suspend fun getRecentBlockHash(): String

    suspend fun getMedianPriorityFee(accounts: List<String>): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>?

    suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson>

    suspend fun getSPLTokensInfo2(tokens: List<String>): List<SplTokenInfo>

    suspend fun getJupiterTokens(): List<JupiterTokenResponseJson>

    suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String?

    suspend fun getTokenAssociatedAccountByOwner(
        walletAddress: String,
        mintAddress: String,
    ): Pair<String?, Boolean>

    suspend fun getFeeForMessage(message: String): BigInteger

    /**
     * The base58 program id that owns [account] on-chain (e.g. the SPL Token vs Token-2022 program
     * that owns a mint), or null if the account is missing or the lookup fails. Used to derive the
     * correct associated-token-account program for a fee mint.
     */
    suspend fun getAccountOwner(account: String): String?

    suspend fun checkStatus(txHash: String): SolanaRpcResponseJson<SolanaSignatureStatusesResult>?

    /**
     * Minimum lamports for rent exemption of an account of the given byte length. The no-arg
     * overload targets SPL token accounts (165 bytes); staking passes the 200-byte stake-account
     * size. Returns [BigInteger.ZERO] on RPC failure.
     */
    suspend fun getMinimumBalanceForRentExemption(dataLength: Int): BigInteger

    /** `getEpochInfo` — current cluster epoch progress, or null when the RPC read fails. */
    suspend fun getEpochInfo(): SolanaEpochInfoResultJson?

    /** `getVoteAccounts` — current + delinquent validator vote accounts, or null on RPC failure. */
    suspend fun getVoteAccounts(): SolanaVoteAccountsResultJson?

    /**
     * The owner's stake accounts (withdrawer authority [ownerAddress]). Discovers the pubkeys with
     * `getProgramAccounts` (`dataSize` + `memcmp`), then re-reads each account's live state with
     * `getAccountInfo` to avoid the lagging secondary index some RPC providers serve
     * `getProgramAccounts` from. Returns them in discovery order, or an empty list on RPC failure.
     * Never stale-cached — always a fresh read.
     */
    suspend fun getStakeAccounts(ownerAddress: String): List<SolanaProgramAccountJson>
}

internal class SolanaApiImp(
    private val json: Json,
    private val httpClient: HttpClient,
    private val splTokenSerializer: SplTokenResponseJsonSerializer,
    // Derives the associated-token-account address for (owner, mint) under the classic SPL or the
    // Token-2022 program. Backed by WalletCore JNI in production; injected so the direct-derivation
    // fallback in [getTokenAssociatedAccountByOwner] can be unit-tested without the native library,
    // which is not loaded in JVM unit tests.
    private val deriveAssociatedTokenAddress:
        (owner: String, mint: String, token2022: Boolean) -> String?,
) : SolanaApi {

    @Inject
    constructor(
        json: Json,
        httpClient: HttpClient,
        splTokenSerializer: SplTokenResponseJsonSerializer,
    ) : this(json, httpClient, splTokenSerializer, ::deriveAssociatedTokenAddressWithWalletCore)

    private val rpcEndpoint = "https://api.vultisig.com/solana/"
    private val splTokensInfoEndpoint = "https://api.solana.fm/v1/tokens"
    private val splTokensInfoEndpoint2 = "$JUPITER_URL/tokens/v2/search"
    private val jupiterTokensUrl = "$JUPITER_URL/tokens/v2/tag"

    override suspend fun getBalance(address: String): BigInteger {
        return try {
            val payload =
                RpcPayload(
                    jsonrpc = "2.0",
                    method = "getBalance",
                    params = buildJsonArray { add(address) },
                    id = 1,
                )
            val response = httpClient.post(rpcEndpoint) { setBody(payload) }
            val rpcResp = response.bodyOrThrow<SolanaBalanceJson>()
            Timber.tag("solanaApiImp").d(response.toString())

            if (rpcResp.error != null) {
                Timber.tag("solanaApiImp")
                    .d("get balance ,address: $address error: ${rpcResp.error}")
                return BigInteger.ZERO
            }
            rpcResp.result?.value ?: error("getBalance error")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            BigInteger.ZERO
        }
    }

    override suspend fun getMinimumBalanceForRentExemption(): BigInteger =
        try {
            httpClient
                .postRpc<SolanaMinimumBalanceForRentExemptionJson>(
                    rpcEndpoint,
                    "getMinimumBalanceForRentExemption",
                    params = buildJsonArray { add(DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION) },
                )
                .result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error getting minimum balance for rent exemption: ${e.message}")
            BigInteger.ZERO
        }

    override suspend fun getRecentBlockHash(): String {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "getLatestBlockhash",
                // `confirmed` is the standard commitment for sending: it tracks the tip closely,
                // whereas `finalized` lags ~32 slots (~13s) behind and burns that much of the
                // ~60-90s blockhash validity window before the keysign ceremony even starts
                // (issue #4863).
                params = buildJsonArray { addJsonObject { put("commitment", "confirmed") } },
                id = 1,
            )
        val response = httpClient.post(rpcEndpoint) { setBody(payload) }
        val responseContent = response.bodyAsText()
        Timber.tag("solanaApiImp").d(responseContent)
        val rpcResp = response.bodyOrThrow<RecentBlockHashResponseJson>()
        if (rpcResp.error != null) {
            error("Solana RPC error: ${rpcResp.error}")
        }
        return rpcResp.result?.value?.blockHash ?: error("getRecentBlockHash error")
    }

    override suspend fun getMedianPriorityFee(accounts: List<String>): BigInteger {
        val fallback = SOLANA_PRIORITY_FEE_PRICE.toBigInteger()
        return try {
            val rpcResp =
                httpClient.postRpc<SolanaFeeObjectRespJson>(
                    url = rpcEndpoint,
                    method = "getRecentPrioritizationFees",
                    params = buildJsonArray { addJsonArray { accounts.forEach { add(it) } } },
                )
            rpcResp.error?.let {
                Timber.tag("SolanaApiImp").w("getRecentPrioritizationFees RPC error: %s", it)
            }
            val nonZeroFees =
                rpcResp.result
                    ?.map { it.prioritizationFee }
                    ?.filter { it > BigInteger.ZERO }
                    ?.sorted()
                    .orEmpty()
            val median = if (nonZeroFees.isEmpty()) fallback else nonZeroFees[nonZeroFees.size / 2]
            maxOf(median, fallback).coerceAtMost(MAX_PRIORITY_FEE_PRICE.toBigInteger())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e(e, "Error getting median priority fee")
            fallback
        }
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val requestBody =
                RpcPayload(
                    jsonrpc = "2.0",
                    method = "sendTransaction",
                    params =
                        buildJsonArray {
                            add(tx)
                            addJsonObject { put("preflightCommitment", "confirmed") }
                        },
                    id = 1,
                )
            repeat(BROADCAST_MAX_ATTEMPTS) { index ->
                val attempt = index + 1
                val response = httpClient.post(rpcEndpoint) { setBody(requestBody) }
                val responseRawString = response.bodyAsText()
                val result = response.bodyOrThrow<BroadcastTransactionRespJson>()
                val error =
                    result.error ?: return result.result ?: error("broadcastTransaction error")

                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
                // Solana puts the actual on-chain reason in error.data.err (e.g.
                // "AccountLoadedTwice",
                // "BlockhashNotFound"); error.message only carries the generic "Transaction
                // simulation
                // failed". Append the reason so the failure surfaced to the user names the real
                // cause
                // instead of a bare, generic message.
                val reason =
                    when (val err = (error["data"] as? JsonObject)?.get("err")) {
                        // Bare string enum, e.g. "AccountLoadedTwice", "BlockhashNotFound".
                        is JsonPrimitive -> err.contentOrNull
                        // Structured error, e.g. {"InstructionError":[1,{"Custom":6001}]}. Use the
                        // variant name (the single top-level key) rather than embedding raw JSON in
                        // the user-facing rejection text.
                        is JsonObject -> err.keys.firstOrNull()
                        else -> null
                    }
                val detailedMessage = if (!reason.isNullOrBlank()) "$message: $reason" else message
                Timber.tag("SolanaApiImp")
                    .d("Error broadcasting transaction: %s", responseRawString)

                // Solana reports the specific failure (e.g. "BlockhashNotFound") in error.data.err,
                // while error.message only carries the generic "Transaction simulation failed".
                // Classify against the whole error object so a transient blockhash-not-found is
                // retried (RESEND) instead of being misread as a fatal error.
                when (solanaBroadcastAction(error.toString(), attempt, BROADCAST_MAX_ATTEMPTS)) {
                    // Propagation lag: the RPC node hasn't observed our confirmed blockhash yet.
                    // Resending the same signed tx after a short backoff typically clears it.
                    SolanaBroadcastAction.RESEND -> {
                        Timber.tag("SolanaApiImp")
                            .w(
                                "Transient blockhash-not-found on broadcast attempt %d/%d; resending after backoff",
                                attempt,
                                BROADCAST_MAX_ATTEMPTS,
                            )
                        delay(BROADCAST_RETRY_BACKOFF)
                    }
                    // True expiry (or exhausted resends): the same tx can no longer land, so
                    // surface it as expired. Re-signing with a fresh blockhash is a deferred
                    // follow-up (needs a cross-device KeysignMessage proto change).
                    SolanaBroadcastAction.EXPIRED ->
                        throw SolanaBlockhashExpiredException(detailedMessage)
                    SolanaBroadcastAction.FATAL -> error(detailedMessage)
                }
            }
            error("broadcastTransaction failed after $BROADCAST_MAX_ATTEMPTS attempts")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag("SolanaApiImp").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }

    override suspend fun getSPLTokensInfo(tokens: List<String>): List<SplTokenJson> {
        try {
            val requestBody = SPLTokenRequestJson(tokens = tokens)
            val response = httpClient.post(splTokensInfoEndpoint) { setBody(requestBody) }
            val responseRawString = response.bodyAsText()
            when (val result = json.decodeFromString(splTokenSerializer, responseRawString)) {
                is SplTokenDeserialized.Error -> {
                    Timber.tag("SolanaApiImp")
                        .d("Error getting spl tokens: ${result.error.error.message}")
                    return emptyList()
                }

                is SplTokenDeserialized.Result -> return result.result.values.toList()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag("SolanaApiImp").e("Error getting spl tokens: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun getSPLTokensInfo2(tokens: List<String>) = coroutineScope {
        tokens
            .map { token ->
                async {
                    try {
                        httpClient
                            .get(splTokensInfoEndpoint2) { parameter("query", token) }
                            .bodyOrThrow<List<SplTokenInfo>>()
                            .firstOrNull()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.tag("SolanaApiImp")
                            .e("Error getting spl token for $token message : ${e.message}")
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    override suspend fun getJupiterTokens(): List<JupiterTokenResponseJson> =
        httpClient
            .get(jupiterTokensUrl) { parameter("query", "verified") }
            .bodyOrThrow<List<JupiterTokenResponseJson>>()

    override suspend fun getSPLTokens(walletAddress: String): List<SplResponseAccountJson>? =
        coroutineScope {
            try {
                val payload = getSplRpcPayload(walletAddress, PROGRAM_ID_SPL_REQUEST_PARAM)
                val payloadToken2022 = getSplRpcPayload(walletAddress, TOKEN_PROGRAM_ID_2022)

                val response = async { httpClient.post(rpcEndpoint) { setBody(payload) } }

                val responseToken2022 = async {
                    httpClient.post(rpcEndpoint) { setBody(payloadToken2022) }
                }

                listOf(response, responseToken2022)
                    .awaitAll()
                    .mapNotNull { it.bodyOrThrow<SplResponseJson>().result?.accounts }
                    .flatten()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
                null
            }
        }

    private fun getSplRpcPayload(address: String, programId: String) =
        RpcPayload(
            jsonrpc = "2.0",
            method = "getTokenAccountsByOwner",
            params =
                buildJsonArray {
                    add(address)
                    addJsonObject { put("programId", programId) }
                    addJsonObject { put("encoding", ENCODING_SPL_REQUEST_PARAM) }
                },
            id = 1,
        )

    override suspend fun getSPLTokenBalance(walletAddress: String, coinAddress: String): String? {
        try {
            val payload =
                RpcPayload(
                    jsonrpc = "2.0",
                    method = "getTokenAccountsByOwner",
                    params =
                        buildJsonArray {
                            add(walletAddress)
                            addJsonObject { put("mint", coinAddress) }
                            addJsonObject { put("encoding", ENCODING_SPL_REQUEST_PARAM) }
                        },
                    id = 1,
                )
            val response = httpClient.post(rpcEndpoint) { setBody(payload) }
            val responseContent = response.bodyAsText()
            Timber.d(responseContent)
            val rpcResp = response.bodyOrThrow<SplAmountRpcResponseJson>()

            if (rpcResp.error != null) {
                Timber.d("get spl token amount error: ${rpcResp.error}")
                return null
            }
            val value = rpcResp.value ?: error("getSPLTokenBalance error")
            if (value.value.isEmpty()) {
                return null
            }
            return value.value[0].account.data.parsed.info.tokenAmount.amount
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            return null
        }
    }

    override suspend fun getTokenAssociatedAccountByOwner(
        walletAddress: String,
        mintAddress: String,
    ): Pair<String?, Boolean> {
        findAssociatedAccountViaOwner(walletAddress, mintAddress)?.let {
            return it
        }
        // The primary getTokenAccountsByOwner lookup came back empty or failed. That can be a real
        // "no account", but it can also be indexer/replica lag right after the ATA was created. If
        // we trusted it, the sender would fail with a false "missing token account" error, and the
        // recipient path would try to recreate an ATA that already exists — a non-idempotent op the
        // on-chain program rejects. Derive the ATA directly and confirm with a point account-info
        // lookup before concluding it truly does not exist. Mirrors iOS (SolanaService).
        return findAssociatedAccountByDerivation(walletAddress, mintAddress)
    }

    /**
     * Primary lookup: ask the indexer for the owner's associated token account for [mintAddress].
     * Returns the (ATA address, isToken2022) pair when the index holds it, or null when the account
     * is absent from the index or the RPC call fails — both of which route the caller to the
     * direct-derivation fallback.
     */
    private suspend fun findAssociatedAccountViaOwner(
        walletAddress: String,
        mintAddress: String,
    ): Pair<String, Boolean>? =
        try {
            val response =
                httpClient.postRpc<SplAmountRpcResponseJson>(
                    url = rpcEndpoint,
                    method = "getTokenAccountsByOwner",
                    params =
                        buildJsonArray {
                            add(walletAddress)
                            addJsonObject { put("mint", mintAddress) }
                            addJsonObject { put("encoding", ENCODING_SPL_REQUEST_PARAM) }
                        },
                )
            if (response.error != null) {
                Timber.tag("SolanaApiImp")
                    .d("getTokenAssociatedAccountByOwner error: %s", response.error)
                null
            } else {
                response.value?.value?.firstOrNull()?.let {
                    Pair(it.pubKey, it.account.owner == TOKEN_PROGRAM_ID_2022)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("SolanaApiImp").e(e, "getTokenAssociatedAccountByOwner failed")
            null
        }

    /**
     * Fallback for when the index has no account or is unreachable: an ATA address is deterministic
     * for a given (owner, mint, token program), so derive both the classic-SPL and Token-2022
     * addresses and confirm on-chain existence with a direct account-info lookup. A given mint is
     * owned by exactly one token program, so at most one derived address can exist. Returns
     * (address, isToken2022) for the one that does, or (null, false) when neither exists.
     */
    private suspend fun findAssociatedAccountByDerivation(
        walletAddress: String,
        mintAddress: String,
    ): Pair<String?, Boolean> {
        for (isToken2022 in listOf(false, true)) {
            val ata =
                deriveAssociatedTokenAddress(walletAddress, mintAddress, isToken2022)?.takeIf {
                    it.isNotEmpty()
                } ?: continue
            // A non-null owner means the account exists on-chain (every account has one); a missing
            // account resolves the info value to null without an RPC error.
            if (getAccountOwner(ata) != null) {
                return Pair(ata, isToken2022)
            }
        }
        Timber.tag("SolanaApiImp")
            .d(
                "getTokenAssociatedAccountByOwner: no ATA for %s / %s after direct-derivation fallback",
                walletAddress,
                mintAddress,
            )
        return Pair(null, false)
    }

    override suspend fun getAccountOwner(account: String): String? =
        try {
            val response =
                httpClient.postRpc<SolanaAccountInfoResponseJson>(
                    url = rpcEndpoint,
                    method = "getAccountInfo",
                    params =
                        buildJsonArray {
                            add(account)
                            addJsonObject { put("encoding", "base64") }
                        },
                )
            // postRpc returns a 200 carrying a JSON-RPC `error` body without throwing, so a
            // transient
            // RPC failure would otherwise resolve to a null owner indistinguishable from a
            // genuinely
            // missing account — and silently drop the now-preferred Jupiter route. Log it so the
            // failure is observable rather than masquerading as "mint not found".
            response.error?.let {
                Timber.tag("SolanaApiImp").w("getAccountOwner RPC error for %s: %s", account, it)
            }
            response.result?.value?.owner
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SolanaApiImp").e(e, "getAccountOwner failed for %s", account)
            null
        }

    override suspend fun getFeeForMessage(message: String): BigInteger {
        val payload =
            RpcPayload(
                jsonrpc = "2.0",
                method = "getFeeForMessage",
                params =
                    buildJsonArray {
                        add(message)
                        addJsonObject { put("commitment", "confirmed") }
                    },
                id = 1,
            )

        val response = httpClient.post(rpcEndpoint) { setBody(payload) }

        val rpcResp = response.bodyOrThrow<SolanaFeeForMessageResponse>()

        return rpcResp.result?.value ?: error("Error fetching getFeeForMessage")
    }

    override suspend fun checkStatus(
        txHash: String
    ): SolanaRpcResponseJson<SolanaSignatureStatusesResult>? {
        try {

            val params = buildJsonArray {
                addJsonArray { add(JsonPrimitive(txHash)) }
                addJsonObject { put("searchTransactionHistory", true) }
            }

            val response: SolanaRpcResponseJson<SolanaSignatureStatusesResult> =
                httpClient.postRpc(
                    url = rpcEndpoint,
                    method = "getSignatureStatuses",
                    params = params,
                )

            return response
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is ClientRequestException && e.response.status == HttpStatusCode.Forbidden) {
                Timber.tag("SolanaApiImp").w("Forbidden (403) when checking tx status: $txHash")
                return null
            }
            throw e
        }
    }

    override suspend fun getMinimumBalanceForRentExemption(dataLength: Int): BigInteger =
        try {
            httpClient
                .postRpc<SolanaMinimumBalanceForRentExemptionJson>(
                    rpcEndpoint,
                    "getMinimumBalanceForRentExemption",
                    params = buildJsonArray { add(dataLength) },
                )
                .result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Error getting minimum balance for rent exemption")
            BigInteger.ZERO
        }

    override suspend fun getEpochInfo(): SolanaEpochInfoResultJson? =
        try {
            val resp =
                httpClient.postRpc<SolanaEpochInfoResponseJson>(
                    rpcEndpoint,
                    "getEpochInfo",
                    params = buildJsonArray {},
                )
            resp.error?.let { Timber.tag("SolanaApiImp").w("getEpochInfo RPC error: %s", it) }
            resp.result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SolanaApiImp").e(e, "Error getting epoch info")
            null
        }

    override suspend fun getVoteAccounts(): SolanaVoteAccountsResultJson? =
        try {
            val resp =
                httpClient.postRpc<SolanaVoteAccountsResponseJson>(
                    rpcEndpoint,
                    "getVoteAccounts",
                    params = buildJsonArray {},
                )
            resp.error?.let { Timber.tag("SolanaApiImp").w("getVoteAccounts RPC error: %s", it) }
            resp.result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SolanaApiImp").e(e, "Error getting vote accounts")
            null
        }

    override suspend fun getStakeAccounts(ownerAddress: String): List<SolanaProgramAccountJson> =
        try {
            // Discover the owner's stake-account pubkeys via getProgramAccounts, but take ONLY the
            // addresses. Many RPC providers serve getProgramAccounts from a lagging secondary
            // index,
            // so its parsed delegation/deactivation fields flip between refreshes right after a
            // stake/unstake (observed on the Defi screen). Each account's live state is re-read
            // below
            // with getAccountInfo — a direct bank read with no index lag. (vultisig-ios #4821)
            val discovery =
                httpClient.postRpc<SolanaProgramAccountsResponseJson>(
                    rpcEndpoint,
                    "getProgramAccounts",
                    params =
                        buildJsonArray {
                            add(SolanaStakingConfig.STAKE_PROGRAM_ID)
                            addJsonObject {
                                put("encoding", "jsonParsed")
                                putJsonArray("filters") {
                                    addJsonObject {
                                        put("dataSize", SolanaStakingConfig.STAKE_ACCOUNT_SPACE)
                                    }
                                    addJsonObject {
                                        putJsonObject("memcmp") {
                                            put(
                                                "offset",
                                                SolanaStakingConfig.WITHDRAWER_MEMCMP_OFFSET,
                                            )
                                            put("bytes", ownerAddress)
                                        }
                                    }
                                }
                            }
                        },
                )
            discovery.error?.let {
                Timber.tag("SolanaApiImp").w("getProgramAccounts RPC error: %s", it)
            }
            val pubkeys = discovery.result.map { it.pubkey }
            val byPubkey =
                coroutineScope {
                        pubkeys
                            .map { pubkey -> async { pubkey to getStakeAccountInfo(pubkey) } }
                            .awaitAll()
                    }
                    .toMap()
            // Preserve discovery order; drop any that stopped resolving (e.g. closed between
            // discovery and the follow-up read).
            pubkeys.mapNotNull { pubkey ->
                byPubkey[pubkey]?.let { SolanaProgramAccountJson(pubkey = pubkey, account = it) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SolanaApiImp").e(e, "Error getting stake accounts for %s", ownerAddress)
            emptyList()
        }

    /**
     * Live state of a single stake account via getAccountInfo (jsonParsed). Null on RPC failure.
     */
    private suspend fun getStakeAccountInfo(pubkey: String): SolanaStakeAccountDataJson? =
        try {
            val response =
                httpClient.postRpc<SolanaStakeAccountInfoResponseJson>(
                    url = rpcEndpoint,
                    method = "getAccountInfo",
                    params =
                        buildJsonArray {
                            add(pubkey)
                            addJsonObject { put("encoding", "jsonParsed") }
                        },
                )
            response.error?.let {
                Timber.tag("SolanaApiImp").w("getAccountInfo RPC error for %s: %s", pubkey, it)
            }
            response.result?.value
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag("SolanaApiImp").e(e, "Error reading stake account %s", pubkey)
            null
        }

    companion object {
        private const val PROGRAM_ID_SPL_REQUEST_PARAM =
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val TOKEN_PROGRAM_ID_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        private const val ENCODING_SPL_REQUEST_PARAM = "jsonParsed"
        private const val DATA_LENGTH_MINIMUM_BALANCE_FOR_RENT_EXEMPTION = 165
        // 100x the floor — caps priority fee at ~0.01 SOL per tx to prevent overpayment
        // during congestion spikes or compromised RPC proxy
        private const val MAX_PRIORITY_FEE_PRICE = 100_000_000L
    }
}

/** Max times a signed Solana tx is (re)sent when the RPC node hasn't yet seen our blockhash. */
private const val BROADCAST_MAX_ATTEMPTS = 3

/** Backoff between rebroadcast attempts, giving the confirmed blockhash time to propagate. */
private val BROADCAST_RETRY_BACKOFF: Duration = 2.seconds

/** Thrown when a Solana broadcast fails because the blockhash has expired (issue #4863). */
class SolanaBlockhashExpiredException(message: String) : Exception(message)

/** What to do with a Solana `sendTransaction` RPC error on a given attempt. */
internal enum class SolanaBroadcastAction {
    /** Transient blockhash-not-found (propagation lag) with retries left — resend the same tx. */
    RESEND,
    /** Blockhash expired (height exceeded, or blockhash-not-found after exhausting retries). */
    EXPIRED,
    /** Any other RPC error — not recoverable by resending. */
    FATAL,
}

/**
 * Classifies a Solana `sendTransaction` error message into the action to take. `-32002` is Solana's
 * generic preflight-failure code, not specific to expired blockhashes, so we match on the message
 * text (mirrors vultisig-ios#4551).
 */
internal fun solanaBroadcastAction(
    errorMessage: String,
    attempt: Int,
    maxAttempts: Int,
): SolanaBroadcastAction {
    // Normalize away spaces/underscores so we match both the human-readable RPC message
    // ("Blockhash not found") and Solana's camelCase TransactionError enum ("BlockhashNotFound").
    val normalized = errorMessage.lowercase().replace(" ", "").replace("_", "")
    return when {
        normalized.contains("blockhashnotfound") && attempt < maxAttempts ->
            SolanaBroadcastAction.RESEND
        normalized.contains("blockhashnotfound") || normalized.contains("blockheightexceeded") ->
            SolanaBroadcastAction.EXPIRED
        else -> SolanaBroadcastAction.FATAL
    }
}

/**
 * Derives the associated-token-account address for [owner] and [mint] under the classic SPL token
 * program ([token2022] = false) or the Token-2022 program ([token2022] = true), via WalletCore.
 * Returns null when WalletCore cannot derive it (e.g. a malformed owner address).
 */
private fun deriveAssociatedTokenAddressWithWalletCore(
    owner: String,
    mint: String,
    token2022: Boolean,
): String? =
    runCatching {
            val address = SolanaAddress(owner)
            if (token2022) address.token2022Address(mint) else address.defaultTokenAddress(mint)
        }
        .getOrNull()
