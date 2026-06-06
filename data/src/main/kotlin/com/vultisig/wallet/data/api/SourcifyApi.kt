package com.vultisig.wallet.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

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
     * it. For an upgradeable proxy, returns the resolved implementation's ABI (via Sourcify's
     * `proxyResolution`) so the proxied functions are labelled rather than the proxy's own
     * `upgradeTo` / `implementation` / `admin` surface.
     *
     * Throws on transport failures so the caller can distinguish a definitive "not verified" (null)
     * from a transient outage (exception) and avoid caching the latter.
     */
    suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray?
}

internal class SourcifyApiImpl @Inject constructor(private val httpClient: HttpClient) :
    SourcifyApi {
    override suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray? {
        val root =
            fetchContract(chainId, contractAddress, withProxyResolution = true) ?: return null
        // For an upgradeable proxy, `fields=abi` returns only the proxy's own ABI (upgradeTo /
        // implementation / admin), never the proxied function we want to label, so resolve the
        // implementation address and fetch its ABI instead. Fall back to the proxy ABI when the
        // implementation can't be fetched.
        proxyImplementationAddress(root)?.let { implementation ->
            fetchContract(chainId, implementation, withProxyResolution = false)
                ?.let { it["abi"] as? JsonArray }
                ?.let {
                    return it
                }
        }
        return root["abi"] as? JsonArray
    }

    private suspend fun fetchContract(
        chainId: String,
        contractAddress: String,
        withProxyResolution: Boolean,
    ): JsonObject? {
        val fields = if (withProxyResolution) "abi,proxyResolution" else "abi"
        val response =
            httpClient.get("$BASE_URL/v2/contract/$chainId/$contractAddress?fields=$fields")
        // A 404 means "no verified contract here" — a definitive answer the caller may cache. Any
        // other non-2xx is treated the same (best-effort enrichment), but a thrown transport error
        // still propagates so transient failures are not cached as "unverified".
        if (response.status != HttpStatusCode.OK) return null
        return parser.parseToJsonElement(response.bodyAsText()) as? JsonObject
    }

    /**
     * Returns the implementation address from [root]'s `proxyResolution` block when the contract is
     * an upgradeable proxy, or null when it isn't a proxy (or no implementation is listed).
     */
    private fun proxyImplementationAddress(root: JsonObject): String? {
        val proxyResolution = root["proxyResolution"] as? JsonObject ?: return null
        if (proxyResolution["isProxy"]?.jsonPrimitive?.booleanOrNull != true) return null
        val implementations = proxyResolution["implementations"] as? JsonArray ?: return null
        return implementations
            .asSequence()
            .mapNotNull { (it as? JsonObject)?.get("address")?.jsonPrimitive?.content }
            .firstOrNull { it.isNotBlank() }
    }

    private companion object {
        private const val BASE_URL = "https://sourcify.dev/server"

        // parseToJsonElement is schema-agnostic, but keep the lenient instance local so a future
        // strict shared Json can't break ABI parsing.
        private val parser = Json { ignoreUnknownKeys = true }
    }
}
