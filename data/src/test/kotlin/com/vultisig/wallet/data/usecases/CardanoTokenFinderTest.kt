package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.models.cardano.CardanoAssetResponseJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.parseCardanoAssetId
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Discovery rules for Cardano native-token holdings surfaced into the asset list.
 *
 * Unlike the XRPL finder, discovery here is not gated to the curated catalog — the long tail is the
 * point — so these pin the two things that keep it honest: a curated asset must not lose its
 * catalog identity, and an uncurated one must not be able to borrow it.
 */
class CardanoTokenFinderTest {

    private val cardanoApi = mockk<CardanoApi>()
    private val finder = CardanoTokenFinderImpl(cardanoApi)

    private val snek = Coins.Cardano.SNEK
    private val snekId = checkNotNull(parseCardanoAssetId(snek.contractAddress))

    private fun row(policyId: String, assetName: String, quantity: String, decimals: Int? = 0) =
        CardanoAssetResponseJson(
            policyId = policyId,
            assetName = assetName,
            quantity = quantity,
            decimals = decimals,
        )

    private suspend fun find(vararg rows: CardanoAssetResponseJson): List<Coin> {
        coEvery { cardanoApi.getAddressAssets(ADDRESS) } returns rows.toList()
        return finder.find(ADDRESS)
    }

    // The headline of the ticket: an asset outside the curated ten becomes a spendable row.
    @Test
    fun `an uncurated asset is surfaced from its on-chain identity`() = runTest {
        val coin =
            find(row(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, quantity = "1500", decimals = 6)).single()

        assertEquals(Chain.Cardano, coin.chain)
        assertEquals("MELD", coin.ticker)
        assertEquals("$MELD_POLICY_ID.$MELD_ASSET_NAME_HEX", coin.contractAddress)
        assertEquals(6, coin.decimal)
        assertEquals(false, coin.isNativeToken)
        // No bundled drawable and no price feed for an asset nobody curated. The ticker must not
        // leak into the logo slot, or a lookalike would borrow a real token's icon.
        assertEquals("", coin.logo)
        assertEquals("", coin.priceProviderID)
    }

    // The catalog entry carries the hand-checked casing, the bundled icon and the price provider,
    // none of which the chain can supply, so it has to win over the derived identity.
    @Test
    fun `a curated asset keeps its catalog identity`() = runTest {
        val coin =
            find(row(snekId.policyId, snekId.assetNameHex, quantity = "76715880000", decimals = 0))
                .single()

        assertEquals(snek, coin)
        assertEquals("snek", coin.logo)
        assertEquals("snek", coin.priceProviderID)
    }

    // USDM's asset name carries the CIP-67 label-333 prefix, whose bytes are unprintable. The
    // curated entry answers this one, but the derivation underneath must not be what saves it.
    @Test
    fun `a curated asset with a CIP-67 name keeps its catalog decimals and ticker`() = runTest {
        val usdm = Coins.Cardano.USDM
        val id = checkNotNull(parseCardanoAssetId(usdm.contractAddress))

        val coin = find(row(id.policyId, id.assetNameHex, quantity = "10", decimals = 0)).single()

        assertEquals(usdm, coin)
        assertEquals(6, coin.decimal)
        assertEquals("USDM", coin.ticker)
    }

    // An unregistered asset that decodes to a curated ticker is exactly the impersonation
    // Coin.id's contract qualification exists to stop. It surfaces, but as its own row.
    @Test
    fun `an imposter asset does not take the curated entry's identity`() = runTest {
        val coin = find(row(MELD_POLICY_ID, SNEK_ASSET_NAME_HEX, quantity = "1")).single()

        assertEquals("SNEK", coin.ticker)
        assertEquals("$MELD_POLICY_ID.$SNEK_ASSET_NAME_HEX", coin.contractAddress)
        assertEquals("", coin.logo)
        // Same ticker, same chain — only the contract qualification separates the two ids.
        assertTrue(coin.id != snek.id, "imposter shares an id with the curated entry: ${coin.id}")
    }

    // Koios reports the holding once per UTxO group, so the same asset arrives several times.
    @Test
    fun `rows for one asset across several utxos collapse to a single coin`() = runTest {
        val coins =
            find(
                row(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, quantity = "500", decimals = 6),
                row(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, quantity = "1000", decimals = 6),
            )

        assertEquals(1, coins.size)
        assertEquals("$MELD_POLICY_ID.$MELD_ASSET_NAME_HEX", coins.single().contractAddress)
    }

    // A wallet that once held an asset can keep a zero row; it is not a holding.
    @Test
    fun `an asset with no remaining quantity is not surfaced`() = runTest {
        assertTrue(find(row(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, quantity = "0")).isEmpty())
    }

    @Test
    fun `an address holding no native tokens yields nothing`() = runTest {
        assertTrue(find().isEmpty())
    }

    // A policy's unnamed asset is legal, and an all-unprintable name is possible; neither can be
    // left with a blank ticker, which would collide with every other blank one.
    @Test
    fun `an unnamed asset falls back to a policy-id prefix`() = runTest {
        val coin = find(row(MELD_POLICY_ID, assetName = "", quantity = "1")).single()

        assertEquals(MELD_POLICY_ID.take(8).uppercase(), coin.ticker)
        assertEquals("$MELD_POLICY_ID.", coin.contractAddress)
    }

    @Test
    fun `an asset named entirely out of printable range falls back to a policy-id prefix`() =
        runTest {
            val coin = find(row(MELD_POLICY_ID, assetName = "00010203", quantity = "1")).single()

            assertEquals(MELD_POLICY_ID.take(8).uppercase(), coin.ticker)
        }

    // A malformed id would be pushed into a signing input's TokenAmount as raw hex and rejected at
    // broadcast, so it is refused at the point it is read instead.
    @Test
    fun `an asset whose id is not well-formed hex is dropped`() = runTest {
        val coins =
            find(
                row(policyId = "not-a-policy-id", assetName = MELD_ASSET_NAME_HEX, quantity = "1"),
                row(MELD_POLICY_ID, assetName = "odd", quantity = "1"),
                row(MELD_POLICY_ID, MELD_ASSET_NAME_HEX, quantity = "1", decimals = 6),
            )

        assertEquals(1, coins.size)
        assertEquals("MELD", coins.single().ticker)
    }

    // A transient Koios blip must not read as "this wallet holds nothing" — the worker only ever
    // adds, so an empty list leaves the vault's existing tokens alone and the next refresh retries.
    @Test
    fun `a failed read yields nothing rather than propagating`() = runTest {
        coEvery { cardanoApi.getAddressAssets(ADDRESS) } throws IOException("offline")

        assertTrue(finder.find(ADDRESS).isEmpty())
    }

    private companion object {
        const val ADDRESS = "addr1test"

        // MELD, named in the ticket as an asset the curated ten miss.
        const val MELD_POLICY_ID = "6ac8ef33b510ec004fe11585f7c5a9f0c07f0c23428ab4f29c1d7d10"
        const val MELD_ASSET_NAME_HEX = "4d454c44"
        const val SNEK_ASSET_NAME_HEX = "534e454b"
    }
}
