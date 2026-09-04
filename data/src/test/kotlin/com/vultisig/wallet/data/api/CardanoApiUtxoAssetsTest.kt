package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.CardanoTokenAsset
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A Cardano transaction has to declare the native assets its inputs carry or the ledger rejects the
 * body for not conserving value, so `address_utxos` has to be read in its extended form and the
 * assets carried onto [com.vultisig.wallet.data.models.payload.UtxoInfo].
 *
 * Ordering is load-bearing too: only the initiator queries Koios and the result is serialized into
 * the keysign payload every co-signer reads, so an unstable order would produce a different session
 * for the same wallet state.
 */
class CardanoApiUtxoAssetsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val ada = Coins.Cardano.ADA.copy(address = "addr1test")

    private val snekPolicyId = "279c909f348e533da5808898f87f9a14bb2c3dfbbacccd631d927a3f"
    private val snekAssetNameHex = "534e454b"
    private val usdmPolicyId = "c48cbb3d5e57ed56e276bc45f99ab39abe94e6cd7ac39fb402da47ad"
    private val usdmAssetNameHex = "0014df105553444d"

    private fun utxoRow(hash: String, index: Int, value: String, assetList: String? = null) =
        buildString {
            append("""{"tx_hash":"$hash","tx_index":$index,"value":"$value"""")
            if (assetList != null) append(""","asset_list":$assetList""")
            append("}")
        }

    private fun assetRow(policyId: String, assetName: String, quantity: String) =
        """{"policy_id":"$policyId","asset_name":"$assetName","quantity":"$quantity"}"""

    @Test
    fun `getUTXOs asks Koios for the extended form`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.capturingRequest(
                        status = HttpStatusCode.OK,
                        body = "[${utxoRow("aa", 0, "1000000")}]",
                        capture = capture,
                        jsonFormat = json,
                    ),
                json = json,
            )

        api.getUTXOs(ada)

        // Without `_extended` Koios omits asset_list entirely and every UTxO looks ADA-only.
        assertTrue(
            capture.bodies.single().contains("\"_extended\":true"),
            "Expected an extended address_utxos request, got ${capture.bodies.single()}",
        )
    }

    @Test
    fun `getUTXOs carries native assets onto the UTxO`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body =
                            "[${utxoRow(
                            hash = "aa",
                            index = 0,
                            value = "1000000",
                            assetList = "[${assetRow(snekPolicyId, snekAssetNameHex, "2500000")}]",
                        )}]",
                        jsonFormat = json,
                    ),
                json = json,
            )

        val asset = api.getUTXOs(ada).single().cardanoTokens.single()

        assertEquals(snekPolicyId, asset.policyId)
        assertEquals(snekAssetNameHex, asset.assetNameHex)
        assertEquals(BigInteger("2500000"), asset.amount)
    }

    @Test
    fun `getUTXOs lowercases the asset id halves`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body =
                            "[${utxoRow(
                            hash = "aa",
                            index = 0,
                            value = "1000000",
                            assetList =
                                "[${assetRow(
                                    snekPolicyId.uppercase(),
                                    snekAssetNameHex.uppercase(),
                                    "1",
                                )}]",
                        )}]",
                        jsonFormat = json,
                    ),
                json = json,
            )

        val asset = api.getUTXOs(ada).single().cardanoTokens.single()

        // The curated catalog stores lowercase ids; a mixed-case wire value would both miss a
        // catalog match and change the bytes a co-signer hashes.
        assertEquals(snekPolicyId, asset.policyId)
        assertEquals(snekAssetNameHex, asset.assetNameHex)
    }

    @Test
    fun `getUTXOs orders UTxOs by hash then index`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body =
                            listOf(
                                    utxoRow("bb", 0, "1"),
                                    utxoRow("aa", 2, "1"),
                                    utxoRow("aa", 1, "1"),
                                )
                                .joinToString(prefix = "[", separator = ",", postfix = "]"),
                        jsonFormat = json,
                    ),
                json = json,
            )

        assertEquals(
            listOf("aa" to 1u, "aa" to 2u, "bb" to 0u),
            api.getUTXOs(ada).map { it.hash to it.index },
        )
    }

    @Test
    fun `getUTXOs orders assets within a UTxO by policy id then asset name`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body =
                            "[${utxoRow(
                            hash = "aa",
                            index = 0,
                            value = "1000000",
                            assetList =
                                listOf(
                                        assetRow(snekPolicyId, snekAssetNameHex, "1"),
                                        assetRow(usdmPolicyId, usdmAssetNameHex, "2"),
                                        assetRow(usdmPolicyId, "", "3"),
                                    )
                                    .joinToString(prefix = "[", separator = ",", postfix = "]"),
                        )}]",
                        jsonFormat = json,
                    ),
                json = json,
            )

        // Koios does not promise an order; the proto has to serialise the same way regardless.
        assertEquals(
            listOf(
                snekPolicyId to snekAssetNameHex,
                usdmPolicyId to "",
                usdmPolicyId to usdmAssetNameHex,
            ),
            api.getUTXOs(ada).single().cardanoTokens.map { it.policyId to it.assetNameHex },
        )
    }

    @Test
    fun `getUTXOs drops a UTxO whose asset row cannot be read`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body =
                            listOf(
                                    utxoRow(
                                        hash = "aa",
                                        index = 0,
                                        value = "1000000",
                                        assetList =
                                            """[{"policy_id":"$snekPolicyId","asset_name":"$snekAssetNameHex","quantity":"not-a-number"}]""",
                                    ),
                                    utxoRow("bb", 0, "2000000"),
                                )
                                .joinToString(prefix = "[", separator = ",", postfix = "]"),
                        jsonFormat = json,
                    ),
                json = json,
            )

        // Spending a UTxO whose bundle was only half read would build a body that silently drops
        // the assets it could not parse, so the whole UTxO is left out of the selection.
        assertEquals(listOf("bb"), api.getUTXOs(ada).map { it.hash })
    }

    @Test
    fun `getUTXOs leaves an ADA-only UTxO with no assets`() = runTest {
        val api =
            CardanoApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(
                        status = HttpStatusCode.OK,
                        body = "[${utxoRow("aa", 0, "1000000", assetList = "[]")}]",
                        jsonFormat = json,
                    ),
                json = json,
            )

        assertEquals(emptyList<CardanoTokenAsset>(), api.getUTXOs(ada).single().cardanoTokens)
    }
}
