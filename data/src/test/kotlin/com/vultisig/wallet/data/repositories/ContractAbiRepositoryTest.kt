@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SourcifyApi
import com.vultisig.wallet.data.models.Chain
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test

internal class ContractAbiRepositoryTest {

    // Drives the repository's injected clock so TTL expiry is deterministic; defaults to 0 so every
    // other test stays comfortably within the TTL window.
    private var now = 0L

    private val addTraitSignature = "addTrait(uint256,uint256,(string,string,bytes,bool,uint256))"

    private val addTraitAbi =
        """
        [
          {"type":"function","name":"addTrait","inputs":[
            {"name":"tokenId","type":"uint256"},
            {"name":"traitId","type":"uint256"},
            {"name":"trait","type":"tuple","components":[
              {"name":"name","type":"string"},
              {"name":"symbol","type":"string"},
              {"name":"data","type":"bytes"},
              {"name":"verified","type":"bool"},
              {"name":"id","type":"uint256"}
            ]}
          ],"outputs":[],"stateMutability":"nonpayable"},
          {"type":"event","name":"TraitAdded","inputs":[]}
        ]
        """
            .trimIndent()

    @Test
    fun `resolves named params and nested tuple components for a matching signature`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        val params = repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)

        assertEquals(listOf("tokenId", "traitId", "trait"), params?.map { it.name })
        val trait = params!![2]
        assertEquals("tuple", trait.type)
        assertEquals(
            listOf("name", "symbol", "data", "verified", "id"),
            trait.components?.map { it.name },
        )
    }

    @Test
    fun `returns null when no abi entry matches the signature`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        assertNull(repo.resolveParams(Chain.Ethereum, CONTRACT, "mintTo(address,uint256)"))
    }

    @Test
    fun `non-evm chain short-circuits without hitting sourcify`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        assertNull(repo.resolveParams(Chain.Bitcoin, CONTRACT, addTraitSignature))
        assertEquals(0, api.calls.get())
    }

    @Test
    fun `whitespace in the signature still matches the canonical abi form`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        val spaced = "addTrait(uint256, uint256, (string, string, bytes, bool, uint256))"
        val params = repo.resolveParams(Chain.Ethereum, CONTRACT, spaced)

        assertEquals(listOf("tokenId", "traitId", "trait"), params?.map { it.name })
    }

    @Test
    fun `repeated lookups for the same contract fetch the abi only once`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)
        repo.resolveParams(Chain.Ethereum, CONTRACT, "mintTo(address,uint256)")
        repo.resolveParams(Chain.Ethereum, CONTRACT.uppercase(), addTraitSignature)

        assertEquals(1, api.calls.get())
    }

    @Test
    fun `unverified contract resolves to null and is not refetched`() = runTest {
        val api = FakeSourcifyApi(abiJson = null)
        val repo = newRepo(api)

        assertNull(repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature))
        assertNull(repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature))
        assertEquals(1, api.calls.get())
    }

    @Test
    fun `transient fetch failure is not cached and is refetched`() = runTest {
        val api = ThrowOnceSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        // First call sees the transient failure (SourcifyApiImpl throws on a non-404 non-OK), so
        // names are unavailable but nothing is cached — the next lookup must hit Sourcify again.
        assertNull(repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature))
        val params = repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)

        assertEquals(listOf("tokenId", "traitId", "trait"), params?.map { it.name })
        assertEquals(2, api.calls.get())
    }

    @Test
    fun `cache entry past ttl is refetched`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val ttl = 1.hours
        val repo = newRepo(api, ttl = ttl)

        now = 0L
        repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)
        // Still inside the TTL window — served from cache, no second fetch.
        now = ttl.inWholeMilliseconds - 1
        repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)
        assertEquals(1, api.calls.get())

        // At/after the TTL the entry is stale and must be refetched.
        now = ttl.inWholeMilliseconds
        val params = repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)
        assertEquals(listOf("tokenId", "traitId", "trait"), params?.map { it.name })
        assertEquals(2, api.calls.get())
    }

    @Test
    fun `least recently used contract is evicted past the cache cap`() = runTest {
        val api = FakeSourcifyApi(addTraitAbi)
        val repo = newRepo(api)

        // MAX_CACHE_ENTRIES in ContractAbiRepositoryImpl; inserting one past the cap evicts eldest.
        val cap = 64
        repeat(cap + 1) { i -> repo.resolveParams(Chain.Ethereum, contractN(i), addTraitSignature) }
        assertEquals(cap + 1, api.calls.get())

        // The eldest entry (#0) was evicted, so looking it up again refetches.
        repo.resolveParams(Chain.Ethereum, contractN(0), addTraitSignature)
        assertEquals(cap + 2, api.calls.get())

        // The most-recently inserted entry is still cached, so it does not refetch.
        repo.resolveParams(Chain.Ethereum, contractN(cap), addTraitSignature)
        assertEquals(cap + 2, api.calls.get())
    }

    @Test
    fun `concurrent lookups for the same contract coalesce into one fetch`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = GatedSourcifyApi(addTraitAbi)
            val repo = newRepo(api)

            val first = async { repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature) }
            api.started.await() // owner is suspended inside the fetch, holding the in-flight slot
            val second = async { repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature) }
            api.gate.complete(Unit) // release the single fetch

            val expected = listOf("tokenId", "traitId", "trait")
            assertEquals(expected, first.await()?.map { it.name })
            assertEquals(expected, second.await()?.map { it.name })
            assertEquals(1, api.calls.get())
        }

    @Test
    fun `follower gets an empty result when the coalesced owner is cancelled`() =
        runTest(UnconfinedTestDispatcher()) {
            val api = GatedSourcifyApi(addTraitAbi)
            val repo = newRepo(api)

            val owner = async { repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature) }
            api.started.await()
            val follower = async { repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature) }

            owner.cancel()
            // The follower must NOT inherit the owner's cancellation — it resolves to "nothing to
            // add" (null), not a thrown CancellationException.
            assertNull(follower.await())

            // The in-flight slot was cleared and nothing was cached, so a later lookup fetches
            // anew.
            api.gate.complete(Unit)
            val later = repo.resolveParams(Chain.Ethereum, CONTRACT, addTraitSignature)
            assertEquals(listOf("tokenId", "traitId", "trait"), later?.map { it.name })
        }

    private fun contractN(index: Int): String = "0x" + index.toString(16).padStart(40, '0')

    private fun newRepo(api: SourcifyApi, ttl: Duration = ContractAbiRepositoryImpl.DEFAULT_TTL) =
        ContractAbiRepositoryImpl(
            sourcifyApi = api,
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = { now },
            ttl = ttl,
        )

    private class FakeSourcifyApi(abiJson: String?) : SourcifyApi {
        val calls = AtomicInteger(0)
        private val abi: JsonArray? = abiJson?.let { Json.parseToJsonElement(it).jsonArray }

        override suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray? {
            calls.incrementAndGet()
            return abi
        }
    }

    private class ThrowOnceSourcifyApi(abiJson: String) : SourcifyApi {
        val calls = AtomicInteger(0)
        private val abi: JsonArray = Json.parseToJsonElement(abiJson).jsonArray

        override suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray? {
            // Mirror SourcifyApiImpl throwing on a transient outage (429/503) the first time.
            if (calls.getAndIncrement() == 0) error("Sourcify 503 Service Unavailable")
            return abi
        }
    }

    /**
     * Fetch suspends on [gate] after signalling [started], so a test can interleave a second
     * concurrent [resolveParams] call and exercise the coalescing / cancellation paths
     * deterministically.
     */
    private class GatedSourcifyApi(abiJson: String) : SourcifyApi {
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        private val abi: JsonArray = Json.parseToJsonElement(abiJson).jsonArray

        override suspend fun fetchAbi(chainId: String, contractAddress: String): JsonArray {
            calls.incrementAndGet()
            started.complete(Unit)
            gate.await()
            return abi
        }
    }

    private companion object {
        const val CONTRACT = "0xe6313d1776e4043d906d5b7221be70cf470f5e87"
    }
}
