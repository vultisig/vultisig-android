package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.SourcifyApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * One ABI parameter — its declared [name] (empty when the ABI left it anonymous), its solidity
 * [type], and, for tuples/structs, the ordered inner [components]. Mirrors the shape of a single
 * `inputs` entry in a standard ABI JSON so it can be zipped against the positional values the
 * verify screen already decodes.
 */
data class AbiParam(val name: String, val type: String, val components: List<AbiParam>? = null)

/**
 * Resolves named parameters for an arbitrary EVM contract call by fetching the contract's verified
 * ABI from Sourcify. Lets the verify screen render `tokenId` / `trait` instead of `#1` / `#3` for
 * contract calls that have no specialised decoder.
 */
interface ContractAbiRepository {
    /**
     * Returns the ordered top-level parameters (with nested tuple [AbiParam.components]) for the
     * function identified by [signature] (e.g. `addTrait(uint256,uint256,(string,string))`) on
     * [contractAddress], or `null` when [chain] is not EVM, the contract is unverified, or no ABI
     * entry matches the signature. Names are best-effort labelling only — callers must still render
     * something useful when this returns `null`.
     */
    suspend fun resolveParams(
        chain: Chain,
        contractAddress: String,
        signature: String,
    ): List<AbiParam>?
}

@Singleton
internal class ContractAbiRepositoryImpl
@Inject
constructor(
    private val sourcifyApi: SourcifyApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContractAbiRepository {

    private var clock: () -> Long = { System.currentTimeMillis() }
    private var ttl: Duration = DEFAULT_TTL

    private val mutex = Mutex()
    // Cache is keyed per contract and holds the whole parsed ABI as canonical-signature -> params.
    // An empty map is a valid, cacheable result: it means "verified-or-not, nothing here we can
    // match" and stops us re-hitting Sourcify for the same contract every time a row renders.
    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Map<String, List<AbiParam>>>>()

    override suspend fun resolveParams(
        chain: Chain,
        contractAddress: String,
        signature: String,
    ): List<AbiParam>? {
        if (chain.standard != TokenStandard.EVM) return null
        val chainId = evmChainId(chain) ?: return null
        val address = contractAddress.trim().takeIf { it.isNotEmpty() } ?: return null
        val canonical = canonicalSignature(signature) ?: return null

        val abi = abiFor(chain, chainId, address)
        return abi[canonical]
    }

    private suspend fun abiFor(
        chain: Chain,
        chainId: String,
        address: String,
    ): Map<String, List<AbiParam>> {
        val key = "${chain.raw}|${address.lowercase()}"

        // Mirror TokenMetadataResolver: take a cache hit, wait on an in-flight fetch, or own one —
        // all under the lock — then suspend on the network outside the lock.
        val slot =
            mutex.withLock {
                cache[key]?.let { cached ->
                    if (clock() - cached.fetchedAt < ttl.inWholeMilliseconds) {
                        return cached.abi
                    }
                }
                inFlight[key]?.let {
                    return@withLock Slot.Wait(it)
                }
                val owned = CompletableDeferred<Map<String, List<AbiParam>>>()
                inFlight[key] = owned
                Slot.Own(owned)
            }

        return when (slot) {
            is Slot.Wait -> slot.deferred.await()
            is Slot.Own -> runFetch(chainId, address, key, slot.deferred)
        }
    }

    private suspend fun runFetch(
        chainId: String,
        address: String,
        key: String,
        owned: CompletableDeferred<Map<String, List<AbiParam>>>,
    ): Map<String, List<AbiParam>> {
        try {
            val abi =
                withContext(ioDispatcher) { sourcifyApi.fetchAbi(chainId, address) }
                    ?.let(::parseAbi) ?: emptyMap()
            mutex.withLock {
                inFlight.remove(key)
                cache[key] = CacheEntry(abi = abi, fetchedAt = clock())
            }
            owned.complete(abi)
            return abi
        } catch (e: CancellationException) {
            mutex.withLock { inFlight.remove(key) }
            owned.cancel(e)
            throw e
        } catch (e: Exception) {
            // Transport failure: complete followers with an empty map (same "nothing to add"
            // contract as everywhere else) but do NOT cache it, so a transient outage doesn't
            // suppress names for the next [ttl] window. `Error` subclasses propagate.
            mutex.withLock { inFlight.remove(key) }
            owned.complete(emptyMap())
            Timber.w(e, "Contract ABI fetch failed for %s on chain %s", address, chainId)
            return emptyMap()
        }
    }

    /** Builds canonical-signature -> top-level-params for every `function` entry in [abi]. */
    private fun parseAbi(abi: JsonArray): Map<String, List<AbiParam>> = buildMap {
        abi.forEach { entry ->
            val obj = entry as? JsonObject ?: return@forEach
            if (obj["type"]?.jsonPrimitive?.content != "function") return@forEach
            val name =
                obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: return@forEach
            val inputs = obj["inputs"]?.jsonArray.orEmpty().mapNotNull(::toAbiParam)
            val canonical = "$name(${inputs.joinToString(",") { canonicalType(it) }})"
            // First definition wins; ABIs don't legitimately duplicate a full signature.
            putIfAbsent(canonical, inputs)
        }
    }

    private fun toAbiParam(element: JsonElement): AbiParam? {
        val obj = element as? JsonObject ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
        val components = (obj["components"] as? JsonArray)?.mapNotNull(::toAbiParam)
        return AbiParam(name = name, type = type, components = components)
    }

    /**
     * Maps an EVM [Chain] to its decimal chain id for Sourcify's path. Doubles as a support
     * allowlist: chains absent here are simply not queried. Unlisted-but-EVM chains return null
     * rather than guessing, and an id Sourcify doesn't index just yields a graceful 404.
     */
    private fun evmChainId(chain: Chain): String? =
        when (chain) {
            Chain.Ethereum -> "1"
            Chain.Optimism -> "10"
            Chain.CronosChain -> "25"
            Chain.BscChain -> "56"
            Chain.Polygon -> "137"
            Chain.ZkSync -> "324"
            Chain.Hyperliquid -> "999"
            Chain.Sei -> "1329"
            Chain.Mantle -> "5000"
            Chain.Base -> "8453"
            Chain.Arbitrum -> "42161"
            Chain.Avalanche -> "43114"
            Chain.Blast -> "81457"
            else -> null
        }

    private data class CacheEntry(val abi: Map<String, List<AbiParam>>, val fetchedAt: Long)

    private sealed interface Slot {
        val deferred: CompletableDeferred<Map<String, List<AbiParam>>>

        data class Wait(override val deferred: CompletableDeferred<Map<String, List<AbiParam>>>) :
            Slot

        data class Own(override val deferred: CompletableDeferred<Map<String, List<AbiParam>>>) :
            Slot
    }

    companion object {
        val DEFAULT_TTL: Duration = 24.hours

        /**
         * Expands an [AbiParam]'s type into its canonical form so it matches a 4byte text signature
         * (`tuple` -> `(t1,t2,...)`, preserving any `[]` array suffix). Recurses through nested
         * tuples. Anonymous/simple types pass through unchanged.
         */
        private fun canonicalType(param: AbiParam): String {
            val type = param.type
            if (!type.startsWith("tuple")) return type
            val arraySuffix = type.removePrefix("tuple") // "", "[]", "[3]" …
            val inner = param.components.orEmpty().joinToString(",") { canonicalType(it) }
            return "($inner)$arraySuffix"
        }

        /**
         * Normalises a 4byte text signature for map lookup: strips whitespace so a signature parsed
         * with incidental spaces still matches the space-free canonical form built from the ABI.
         * Returns null when the signature has no parenthesised parameter list.
         */
        private fun canonicalSignature(signature: String): String? {
            val stripped = signature.filterNot { it.isWhitespace() }
            if (!stripped.contains('(') || !stripped.endsWith(')')) return null
            return stripped
        }
    }
}
