@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SourcifyApi
import com.vultisig.wallet.data.models.Chain
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test

internal class ContractAbiRepositoryTest {

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

    private fun newRepo(api: SourcifyApi) =
        ContractAbiRepositoryImpl(sourcifyApi = api, ioDispatcher = UnconfinedTestDispatcher())

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

    private companion object {
        const val CONTRACT = "0xe6313d1776e4043d906d5b7221be70cf470f5e87"
    }
}
