package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiCoinMetadata
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vultisig.keysign.v1.SuiCoin

/**
 * Covers Sui held-token auto-discovery (issue #5443): a coin type outside the curated
 * `Coins.Sui.all` catalog used to be unreachable in the UI however it was acquired.
 */
internal class SuiTokenFinderTest {

    private val suiApi: SuiApi = mockk()

    private val finder = SuiTokenFinderImpl(suiApi)

    @Test
    fun `discovers a held coin type the curated catalog does not list`() = runTest {
        stubHeld(heldObject(GOLD_TYPE))
        coEvery { suiApi.getCoinMetadata(GOLD_TYPE) } returns
            SuiCoinMetadata(decimals = 6, symbol = "GOLD", iconUrl = GOLD_ICON)

        val discovered = finder.find(ADDRESS).single()

        assertEquals(Chain.Sui, discovered.chain)
        assertEquals("GOLD", discovered.ticker)
        assertEquals(6, discovered.decimal)
        assertEquals(GOLD_TYPE, discovered.contractAddress)
        assertEquals(GOLD_ICON, discovered.logo)
        assertFalse(discovered.isNativeToken)
        // The address is stamped on later, by whoever enables the token into a vault.
        assertEquals("", discovered.address)
    }

    @Test
    fun `resolves a held coin type to its curated entry when the node reports it short-form`() =
        runTest {
            // The catalog stores CETUS's package address zero-padded and a node may answer with it
            // stripped. A raw string compare would miss and republish a known token under its
            // on-chain symbol with no price source.
            val cetus = Coins.Sui.CETUS
            val shortForm = withShortPackageAddress(cetus.contractAddress)
            assertNotEquals(cetus.contractAddress, shortForm)
            stubHeld(heldObject(shortForm))

            val discovered = finder.find(ADDRESS)

            assertEquals(listOf(cetus), discovered)
            coVerify(exactly = 0) { suiApi.getCoinMetadata(any()) }
        }

    @Test
    fun `skips native sui in both the short and the zero-padded address form`() = runTest {
        stubHeld(heldObject(NATIVE_TYPE, "0x1"), heldObject(PADDED_NATIVE_TYPE, "0x2"))

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
        coVerify(exactly = 0) { suiApi.getCoinMetadata(any()) }
    }

    @Test
    fun `collapses every coin object of one type into a single token`() = runTest {
        stubHeld(
            heldObject(GOLD_TYPE, "0x1"),
            heldObject(GOLD_TYPE, "0x2"),
            heldObject(withShortPackageAddress(GOLD_TYPE), "0x3"),
        )
        coEvery { suiApi.getCoinMetadata(any()) } returns
            SuiCoinMetadata(decimals = 6, symbol = "GOLD", iconUrl = null)

        assertEquals(1, finder.find(ADDRESS).size)
        coVerify(exactly = 1) { suiApi.getCoinMetadata(any()) }
    }

    @Test
    fun `a failed metadata read drops only its own coin`() = runTest {
        stubHeld(heldObject(GOLD_TYPE, "0x1"), heldObject(SILVER_TYPE, "0x2"))
        coEvery { suiApi.getCoinMetadata(GOLD_TYPE) } throws
            IllegalStateException("node overloaded")
        coEvery { suiApi.getCoinMetadata(SILVER_TYPE) } returns
            SuiCoinMetadata(decimals = 9, symbol = "SILVER", iconUrl = null)

        assertEquals(listOf("SILVER"), finder.find(ADDRESS).map { it.ticker })
    }

    @Test
    fun `drops a coin the node publishes no metadata for`() = runTest {
        stubHeld(heldObject(GOLD_TYPE))
        coEvery { suiApi.getCoinMetadata(GOLD_TYPE) } returns null

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
    }

    @Test
    fun `drops a coin whose metadata carries a blank symbol`() = runTest {
        stubHeld(heldObject(GOLD_TYPE))
        coEvery { suiApi.getCoinMetadata(GOLD_TYPE) } returns
            SuiCoinMetadata(decimals = 6, symbol = "   ", iconUrl = null)

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
    }

    @Test
    fun `drops a coin whose metadata reports decimals outside the on-chain u8 range`() = runTest {
        // Negative decimals shift every displayed balance the wrong way, inflating it; an
        // out-of-range positive one blows up the BigDecimal scaling that renders the balance.
        stubHeld(heldObject(GOLD_TYPE, "0x1"), heldObject(SILVER_TYPE, "0x2"))
        coEvery { suiApi.getCoinMetadata(GOLD_TYPE) } returns
            SuiCoinMetadata(decimals = -6, symbol = "GOLD", iconUrl = null)
        coEvery { suiApi.getCoinMetadata(SILVER_TYPE) } returns
            SuiCoinMetadata(decimals = 1_000_000_000, symbol = "SILVER", iconUrl = null)

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
    }

    @Test
    fun `ignores a coin object reported without a coin type`() = runTest {
        stubHeld(heldObject(coinType = ""))

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
        coVerify(exactly = 0) { suiApi.getCoinMetadata(any()) }
    }

    @Test
    fun `returns empty when the held-coin read fails`() = runTest {
        coEvery { suiApi.getAllCoins(ADDRESS) } throws IllegalStateException("invalid address")

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
    }

    @Test
    fun `propagates cancellation instead of reporting an empty wallet`() = runTest {
        coEvery { suiApi.getAllCoins(ADDRESS) } throws CancellationException("navigated away")

        assertThrows<CancellationException> { finder.find(ADDRESS) }
    }

    @Test
    fun `returns empty for an address holding nothing`() = runTest {
        stubHeld()

        assertEquals(emptyList<Coin>(), finder.find(ADDRESS))
        coVerify(exactly = 0) { suiApi.getCoinMetadata(any()) }
    }

    @Test
    fun `a discovered token selects its own objects when the node later pads the coin type`() =
        runTest {
            // Discovery stores whichever form the node answered with. The send that follows
            // re-reads the coin objects, and a node — or a custom RPC — may then pad the package
            // address. Both ends have to resolve to the same coin type, or the send embeds no
            // token objects and fails at signing.
            val shortForm = withShortPackageAddress(GOLD_TYPE)
            assertNotEquals(GOLD_TYPE, shortForm)
            stubHeld(heldObject(shortForm, "0xt1", "500"))
            coEvery { suiApi.getCoinMetadata(shortForm) } returns
                SuiCoinMetadata(decimals = 6, symbol = "GOLD", iconUrl = null)

            val discovered = finder.find(ADDRESS).single()
            assertEquals(shortForm, discovered.contractAddress)

            val reReadLater =
                listOf(heldObject(GOLD_TYPE, "0xt1", "500"), heldObject(NATIVE_TYPE, "0xg1", GAS))
            assertTrue(selectedObjectIds(reReadLater, discovered.contractAddress).contains("0xt1"))
        }

    @Test
    fun `orders tokens by coin type, not by the order the node listed the objects`() = runTest {
        // Two coin types may publish the same symbol, and a Sui Coin.id is its ticker alone, so the
        // token list keeps only the first of them. Node object order would make that an arbitrary
        // pick that flips between refreshes, silently changing what the row stands for.
        val gold = heldObject(GOLD_TYPE, "0x1")
        val impostor = heldObject(SILVER_TYPE, "0x2")
        coEvery { suiApi.getCoinMetadata(any()) } returns
            SuiCoinMetadata(decimals = 6, symbol = "GOLD", iconUrl = null)

        stubHeld(gold, impostor)
        val oneOrder = finder.find(ADDRESS)
        stubHeld(impostor, gold)
        val otherOrder = finder.find(ADDRESS)

        assertEquals(oneOrder, otherOrder)
        assertEquals(GOLD_TYPE, oneOrder.first().contractAddress)
    }

    @Test
    fun `a curated token discovered short-form still selects its own objects at signing`() =
        runTest {
            // Same invariant across the catalog path, where the contract address the coin carries
            // is the catalog's zero-padded form rather than the string the node answered with.
            val shortForm = withShortPackageAddress(Coins.Sui.CETUS.contractAddress)
            val held =
                listOf(heldObject(shortForm, "0xt1", "500"), heldObject(NATIVE_TYPE, "0xg1", GAS))
            coEvery { suiApi.getAllCoins(ADDRESS) } returns held

            val discovered = finder.find(ADDRESS).single()

            assertEquals(Coins.Sui.CETUS.contractAddress, discovered.contractAddress)
            assertTrue(selectedObjectIds(held, discovered.contractAddress).contains("0xt1"))
        }

    /** The coin objects a token send of [contractAddress] would embed in its keysign payload. */
    private fun selectedObjectIds(held: List<SuiCoin>, contractAddress: String): List<String> =
        SuiHelper.selectPayloadCoins(
                coins = held,
                isNativeToken = false,
                contractAddress = contractAddress,
                amount = BigInteger.valueOf(500),
                gasBudget = BigInteger.valueOf(3_000_000),
            )
            .map { it.coinObjectId }

    private fun stubHeld(vararg objects: SuiCoin) {
        coEvery { suiApi.getAllCoins(ADDRESS) } returns objects.toList()
    }

    private fun heldObject(coinType: String, objectId: String = "0x1", balance: String = "10") =
        SuiCoin(
            coinType = coinType,
            coinObjectId = objectId,
            version = "1",
            digest = "digest-$objectId",
            balance = balance,
            previousTransaction = "",
        )

    /** [coinType] with its package address stripped of leading zeros, as a node may report it. */
    private fun withShortPackageAddress(coinType: String): String {
        val separator = coinType.indexOf("::")
        val address = coinType.substring(0, separator).removePrefix("0x").trimStart('0')
        return "0x$address${coinType.substring(separator)}"
    }

    private companion object {
        const val ADDRESS = "0xf00d"
        const val GAS = "1000000000"
        const val NATIVE_TYPE = "0x2::sui::SUI"
        const val PADDED_NATIVE_TYPE =
            "0x0000000000000000000000000000000000000000000000000000000000000002::sui::SUI"
        const val GOLD_TYPE =
            "0x0a2b3c4d5e6f7809000000000000000000000000000000000000000000000001::gold::GOLD"
        const val SILVER_TYPE =
            "0x0a2b3c4d5e6f7809000000000000000000000000000000000000000000000002::silver::SILVER"
        const val GOLD_ICON = "https://example.test/gold.png"
    }
}
