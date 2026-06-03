package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Thin client over Sourcify's verified-contract repository. Used by
 * [com.vultisig.wallet.data.repositories.ContractAbiRepository] to recover human-readable parameter
 * names for arbitrary EVM contract calls that no specialised decoder handles.
 *
 * Sourcify is keyless and public, so this needs no API token. Only the function input metadata
 * (names + types) is consumed downstream — the ABI is never used to execute or re-encode anything,
 * it only labels rows on the read-only verify screen.
 */
internal interface SourcifyApi {
    /**
     * Returns the verified ABI for [contractAddress] on the EVM chain [chainId] (decimal string,
     * e.g. `"1"` for Ethereum mainnet) as a raw JSON array, or `null` when the contract is not
     * verified on Sourcify. [contractAddress] may be lower-case — Sourcify's v2 endpoint normalises
     * it.
     *
     * Throws on transport failures so the caller can distinguish a definitive "not verified" (null)
     * from a transient outage (exception) and avoid caching the latter.
     */
    suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray?
}

internal class SourcifyApiImpl @Inject constructor(private val httpClient: HttpClient) :
    SourcifyApi {
    override suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray? {
        val response = httpClient.get("$BASE_URL/v2/contract/$chainId/$contractAddress?fields=abi")
        // A 404 means "no verified contract here" — a definitive answer the caller may cache. Any
        // other non-2xx is treated the same (best-effort enrichment), but a thrown transport error
        // still propagates so transient failures are not cached as "unverified".
        if (response.status != HttpStatusCode.OK) return null
        val root = parser.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        return root["abi"] as? JsonArray
    }

    private companion object {
        private const val BASE_URL = "https://sourcify.dev/server"

        // parseToJsonElement is schema-agnostic, but keep the lenient instance local so a future
        // strict shared Json can't break ABI parsing.
        private val parser = Json { ignoreUnknownKeys = true }
    }
}
