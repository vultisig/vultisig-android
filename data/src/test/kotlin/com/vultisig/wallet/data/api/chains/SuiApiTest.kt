package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [SuiApiImpl] over Sui GraphQL RPC (issue #5506).
 *
 * Two guarantees carried over from the JSON-RPC implementation this replaces: every call must
 * surface the node's real error text rather than a hardcoded generic string (#5444), and
 * `checkStatus` must tell a digest that hasn't landed yet apart from a node refusal. On top of
 * those, the mapping from the GraphQL response shape back onto the app's models is pinned here —
 * the coin-type unwrap and the status lowercasing in particular, since either one failing silently
 * misclassifies a transfer or a confirmation rather than throwing.
 */
class SuiApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK): SuiApiImpl =
        SuiApiImpl(MockHttpClient.respondingWith(status, body), json)

    private fun errorBody(message: String, code: String = "BAD_USER_INPUT") =
        """{"data":null,"errors":[{"message":"$message","extensions":{"code":"$code"}}]}"""

    @Test
    fun `getBalance throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("invalid sui address"))

        val e = assertFailsWith<SuiRpcException> { api.getBalance("0xabc", "") }
        assertTrue(e.message!!.contains("invalid sui address"), "message was: ${e.message}")
        assertTrue(e.message!!.contains("BAD_USER_INPUT"), "message was: ${e.message}")
    }

    @Test
    fun `getBalance returns parsed amount on success`() = runTest {
        val api = api("""{"data":{"address":{"balance":{"totalBalance":"42"}}}}""")

        assertEquals(BigInteger.valueOf(42), api.getBalance("0xabc", ""))
    }

    // A null balance is the node reading with no checkpoint in scope — an absent reading, not a
    // failed one. (A coin type the address has never held comes back as a real zero instead.)
    // Raising here would surface an error banner in place of an ordinary empty balance.
    @Test
    fun `getBalance reads a null balance as zero, not an error`() = runTest {
        val api = api("""{"data":{"address":{"balance":null}}}""")

        assertEquals(BigInteger.ZERO, api.getBalance("0xabc", ""))
    }

    // An absent balance is a genuine zero; an amount the node sent but that cannot be parsed is a
    // contract violation. Collapsing both into zero would show someone an empty wallet for a bug.
    @Test
    fun `getBalance rejects an unparsable balance instead of reading it as zero`() = runTest {
        val api = api("""{"data":{"address":{"balance":{"totalBalance":"not-a-number"}}}}""")

        val e = assertFailsWith<SuiRpcException> { api.getBalance("0xabc", "") }
        assertTrue(e.message!!.contains("not-a-number"), "message was: ${e.message}")
    }

    // A shape the selection set cannot describe is a node contract violation. Decoding it into a
    // fallback would report someone's real balance as zero, so it surfaces instead.
    @Test
    fun `getBalance rejects a response whose shape does not match the query`() = runTest {
        val api = api("""{"data":{"address":{"balance":{"totalBalance":{"nested":1}}}}}""")

        val e = assertFailsWith<SuiRpcException> { api.getBalance("0xabc", "") }
        assertTrue(e.errorMessage.contains("unexpected response shape"), e.errorMessage)
    }

    @Test
    fun `getBalance asks for the native coin type when no contract address is given`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            SuiApiImpl(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    """{"data":{"address":{"balance":{"totalBalance":"1"}}}}""",
                    capture,
                ),
                json,
            )

        api.getBalance("0xabc", "")

        assertTrue(capture.lastBody.contains("0x2::sui::SUI"), capture.lastBody)
    }

    @Test
    fun `getReferenceGasPrice throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("node overloaded", code = "INTERNAL_SERVER_ERROR"))

        val e = assertFailsWith<SuiRpcException> { api.getReferenceGasPrice() }
        assertTrue(e.message!!.contains("node overloaded"), "message was: ${e.message}")
        assertTrue(e.message!!.contains("INTERNAL_SERVER_ERROR"), "message was: ${e.message}")
    }

    @Test
    fun `getReferenceGasPrice returns the parsed price`() = runTest {
        val api = api("""{"data":{"epoch":{"referenceGasPrice":"750"}}}""")

        assertEquals(BigInteger.valueOf(750), api.getReferenceGasPrice())
    }

    @Test
    fun `getAllCoins throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("invalid address"))

        val e = assertFailsWith<SuiRpcException> { api.getAllCoins("0xabc") }
        assertTrue(e.message!!.contains("invalid address"), "message was: ${e.message}")
    }

    // The object connection reports the wrapper struct `0x2::coin::Coin<T>`, while the rest of the
    // app compares against the bare `T`. A wrapper leaking through here would match no known coin
    // and silently turn a native SUI send into a token send.
    @Test
    fun `getAllCoins unwraps the coin type out of the Coin wrapper`() = runTest {
        val api = api(coinsPage(hasNextPage = false))

        val coins = api.getAllCoins("0xabc")

        assertEquals(1, coins.size)
        assertEquals("0x2::sui::SUI", coins.single().coinType)
        assertEquals("0xcoin1", coins.single().coinObjectId)
        assertEquals("600", coins.single().balance)
        assertEquals("100", coins.single().version)
        assertEquals("digest-1", coins.single().digest)
        assertEquals("prev-1", coins.single().previousTransaction)
    }

    // GraphQL always spells the address zero-padded where JSON-RPC returned it stripped. Verified
    // against a live mainnet address: with this normalization the coin types match
    // `suix_getAllCoins` exactly, so a token discovered after the migration keeps the same
    // persisted contractAddress as one discovered before it.
    @Test
    fun `getAllCoins strips the zero padding from the coin type address`() = runTest {
        val api =
            api(
                """
                {"data":{"address":{"objects":{
                  "pageInfo":{"hasNextPage":false,"endCursor":null},
                  "nodes":[{
                    "address":"0xcoin1","version":1,"digest":"d","previousTransaction":{"digest":"p"},
                    "contents":{"type":{"repr":"0x0000000000000000000000000000000000000000000000000000000000000002::coin::Coin<0x0000000000000000000000000000000000000000000000000000000000000002::sui::SUI>"},
                    "json":{"balance":"1"}}
                  }]
                }}}}
                """
                    .trimIndent()
            )

        assertEquals("0x2::sui::SUI", api.getAllCoins("0xabc").single().coinType)
    }

    // Only the address is normalized — `::coin::USDC` and `::coin::usdc` are distinct Move types,
    // so lowercasing the whole string would collapse two different coins into one.
    @Test
    fun `getAllCoins leaves module and struct identifiers case-sensitive`() = runTest {
        val api =
            api(
                """
                {"data":{"address":{"objects":{
                  "pageInfo":{"hasNextPage":false,"endCursor":null},
                  "nodes":[{
                    "address":"0xcoin1","version":1,"digest":"d","previousTransaction":{"digest":"p"},
                    "contents":{"type":{"repr":"0x2::coin::Coin<0x00ABC::myCoin::USDC>"},
                    "json":{"balance":"1"}}
                  }]
                }}}}
                """
                    .trimIndent()
            )

        assertEquals("0xabc::myCoin::USDC", api.getAllCoins("0xabc").single().coinType)
    }

    // A wallet whose coin objects span multiple pages must return the whole set — a token whose
    // objects all land on a later page would otherwise be invisible to the send flow. The second
    // request must carry the cursor the first page returned; without that assertion the walk could
    // be re-reading page one and the sequenced mock would still look correct.
    @Test
    fun `getAllCoins follows pagination and forwards the endCursor`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to
                    coinsPage(
                        hasNextPage = true,
                        objectId = "0xcoin1",
                        endCursor = "page-1-cursor",
                    ),
                HttpStatusCode.OK to
                    coinsPage(
                        hasNextPage = false,
                        objectId = "0xcoin2",
                        endCursor = "page-2-cursor",
                    ),
            )

        val coins = SuiApiImpl(client, json).getAllCoins("0xabc")

        assertEquals(listOf("0xcoin1", "0xcoin2"), coins.map { it.coinObjectId })
        assertEquals(2, capture.bodies.size)
        assertTrue(capture.bodies[0].contains("\"cursor\":null"), capture.bodies[0])
        assertTrue(capture.bodies[1].contains("page-1-cursor"), capture.bodies[1])
    }

    // Termination must not rest on the node alone. A connection that keeps reporting hasNextPage
    // while handing back the same cursor would otherwise loop forever, growing the coin list until
    // the process dies. Ending the walk quietly is not enough either: the coins collected so far
    // are not the wallet, and coin selection has no way to tell the difference.
    @Test
    fun `getAllCoins raises when the endCursor does not advance`() = runTest {
        // respondingWithSequence pins the last entry, so this page repeats indefinitely.
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.OK to
                    coinsPage(hasNextPage = true, objectId = "0xcoin1", endCursor = "stuck")
            )

        val e = assertFailsWith<SuiRpcException> { SuiApiImpl(client, json).getAllCoins("0xabc") }
        assertTrue(e.errorMessage.contains("stalled"), e.errorMessage)
    }

    // A node that advances the cursor forever is bounded by the page budget rather than by trust,
    // and exhausting that budget is reported rather than absorbed — 5000 coin objects is a
    // misbehaving connection, and a send built from a truncated list fails as a bogus
    // "insufficient balance" with nothing naming the real cause.
    @Test
    fun `getAllCoins raises at the page budget when the cursor keeps advancing`() = runTest {
        val client =
            MockHttpClient.respondingWithGenerated { page ->
                coinsPage(hasNextPage = true, objectId = "0xcoin$page", endCursor = "cursor-$page")
            }

        val e = assertFailsWith<SuiRpcException> { SuiApiImpl(client, json).getAllCoins("0xabc") }
        assertTrue(e.errorMessage.contains("$SUI_MAX_COIN_PAGES page budget"), e.errorMessage)
    }

    // The coin type of an LP or wrapper token is itself a generic instantiation, and GraphQL
    // zero-pads its nested addresses too. Normalizing only the outermost one leaves the same token
    // spelled differently than the pre-migration node spelled it, and the stored contractAddress
    // then matches no coin object — which blocks that token's send outright.
    @Test
    fun `getAllCoins strips the zero padding from nested generic coin types`() = runTest {
        val paddedTwoB = PADDED_TWO.dropLast(2) + "2b"
        val nested =
            "$PADDED_TWO::coin::Coin<$PADDED_TWO::spot_dex::LP<$PADDED_TWO::sui::SUI," +
                "$paddedTwoB::coin::COIN>>"
        val api = api(coinsPage(hasNextPage = false, repr = nested))

        assertEquals(
            "0x2::spot_dex::LP<0x2::sui::SUI,0x2b::coin::COIN>",
            api.getAllCoins("0xabc").single().coinType,
        )
    }

    // A primitive type argument carries no address, so it must survive normalization untouched —
    // treating `u64` as an address would rewrite it into a type that names nothing.
    @Test
    fun `getAllCoins leaves primitive type arguments alone`() = runTest {
        val api =
            api(
                coinsPage(
                    hasNextPage = false,
                    repr = "$PADDED_TWO::coin::Coin<$PADDED_TWO::table::Table<u64,bool>>",
                )
            )

        assertEquals("0x2::table::Table<u64,bool>", api.getAllCoins("0xabc").single().coinType)
    }

    // objectId, version and digest are exactly the fields that become the signed Sui.ObjectRef.
    // Substituting a blank for a missing one commits every device in the ceremony to bytes the
    // network rejects at broadcast; a blank version does not even get that far, throwing on the
    // toLong() inside SuiHelper.
    @Test
    fun `getAllCoins drops a coin object missing its version or digest`() = runTest {
        val api =
            api(
                """
                {"data":{"address":{"objects":{
                  "pageInfo":{"hasNextPage":false,"endCursor":null},
                  "nodes":[
                    {"address":"0xcoin1","version":null,"digest":"d",
                     "previousTransaction":{"digest":"p"},
                     "contents":{"type":{"repr":"0x2::coin::Coin<0x2::sui::SUI>"},
                     "json":{"balance":"1"}}},
                    {"address":"0xcoin2","version":1,"digest":null,
                     "previousTransaction":{"digest":"p"},
                     "contents":{"type":{"repr":"0x2::coin::Coin<0x2::sui::SUI>"},
                     "json":{"balance":"1"}}},
                    {"address":null,"version":1,"digest":"d",
                     "previousTransaction":{"digest":"p"},
                     "contents":{"type":{"repr":"0x2::coin::Coin<0x2::sui::SUI>"},
                     "json":{"balance":"1"}}},
                    {"address":"0xcoin4","version":4,"digest":"d4",
                     "previousTransaction":{"digest":"p"},
                     "contents":{"type":{"repr":"0x2::coin::Coin<0x2::sui::SUI>"},
                     "json":{"balance":"1"}}}
                  ]
                }}}}
                """
                    .trimIndent()
            )

        assertEquals(listOf("0xcoin4"), api.getAllCoins("0xabc").map { it.coinObjectId })
    }

    // A connection whose non-null pageInfo/nodes are absent is a malformed response, not an empty
    // page. Decoding it into empty defaults would end the walk early and report a wallet's coins
    // as the subset read so far.
    @Test
    fun `getAllCoins rejects a connection missing pageInfo or nodes`() = runTest {
        val e =
            assertFailsWith<SuiRpcException> {
                api("""{"data":{"address":{"objects":{"nodes":[]}}}}""").getAllCoins("0xabc")
            }
        assertTrue(e.errorMessage.contains("unexpected response shape"), e.errorMessage)

        val missingNodes =
            assertFailsWith<SuiRpcException> {
                api(
                        """{"data":{"address":{"objects":{"pageInfo":{"hasNextPage":false,"endCursor":null}}}}}"""
                    )
                    .getAllCoins("0xabc")
            }
        assertTrue(missingNodes.errorMessage.contains("unexpected response shape"), e.errorMessage)
    }

    @Test
    fun `getCoinMetadata throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("bad coin type"))

        val e = assertFailsWith<SuiRpcException> { api.getCoinMetadata(COIN_TYPE) }
        assertTrue(e.message!!.contains("bad coin type"), "message was: ${e.message}")
    }

    @Test
    fun `getCoinMetadata returns null when the coin publishes no metadata`() = runTest {
        val api = api("""{"data":{"coinMetadata":null}}""")

        assertNull(api.getCoinMetadata(COIN_TYPE))
    }

    @Test
    fun `getCoinMetadata returns the parsed metadata on success`() = runTest {
        val api =
            api(
                """{"data":{"coinMetadata":{"decimals":6,"symbol":"GOLD","iconUrl":"https://example.test/gold.png"}}}"""
            )

        val metadata = api.getCoinMetadata(COIN_TYPE)

        assertEquals(6, metadata?.decimals)
        assertEquals("GOLD", metadata?.symbol)
        assertEquals("https://example.test/gold.png", metadata?.iconUrl)
    }

    @Test
    fun `getCoinMetadata leaves an absent iconUrl null`() = runTest {
        val api = api("""{"data":{"coinMetadata":{"decimals":9,"symbol":"SILVER"}}}""")

        assertNull(api.getCoinMetadata(COIN_TYPE)?.iconUrl)
    }

    // A coin the node cannot fully describe must be dropped rather than rendered at a guessed
    // magnitude — showing a balance with the wrong decimals misstates what the user holds.
    @Test
    fun `getCoinMetadata returns null when decimals or symbol are missing`() = runTest {
        assertNull(
            api("""{"data":{"coinMetadata":{"symbol":"GOLD"}}}""").getCoinMetadata(COIN_TYPE)
        )
        assertNull(api("""{"data":{"coinMetadata":{"decimals":6}}}""").getCoinMetadata(COIN_TYPE))
    }

    @Test
    fun `getCoinMetadata asks the node for the requested coin type`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            SuiApiImpl(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    """{"data":{"coinMetadata":{"decimals":6,"symbol":"GOLD"}}}""",
                    capture,
                ),
                json,
            )

        api.getCoinMetadata(COIN_TYPE)

        assertTrue(capture.lastBody.contains("coinMetadata"), capture.lastBody)
        assertTrue(capture.lastBody.contains(COIN_TYPE), capture.lastBody)
    }

    @Test
    fun `executeTransactionBlock throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("GasBalanceTooLow"))

        val e = assertFailsWith<SuiRpcException> { api.executeTransactionBlock("tx-bytes", "sig") }
        assertTrue(e.message!!.contains("GasBalanceTooLow"), "message was: ${e.message}")
    }

    @Test
    fun `executeTransactionBlock returns the transaction digest`() = runTest {
        val api = api("""{"data":{"executeTransaction":{"effects":{"digest":"tx-digest"}}}}""")

        assertEquals("tx-digest", api.executeTransactionBlock("tx-bytes", "sig"))
    }

    // The signed bytes must reach the node as the BCS payload; sending them under the wrong key
    // would be rejected at execution time rather than caught here.
    @Test
    fun `executeTransactionBlock sends the signed bytes and signature`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            SuiApiImpl(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    """{"data":{"executeTransaction":{"effects":{"digest":"d"}}}}""",
                    capture,
                ),
                json,
            )

        api.executeTransactionBlock("tx-bytes", "the-signature")

        assertTrue(capture.lastBody.contains("tx-bytes"), capture.lastBody)
        assertTrue(capture.lastBody.contains("the-signature"), capture.lastBody)
    }

    @Test
    fun `dryRunTransaction throws with the real GraphQL error message and code`() = runTest {
        val api = api(errorBody("invalid transaction bytes"))

        val e = assertFailsWith<SuiRpcException> { api.dryRunTransaction("tx-bytes") }
        assertTrue(e.message!!.contains("invalid transaction bytes"), "message was: ${e.message}")
    }

    @Test
    fun `dryRunTransaction maps the gas summary onto the effects`() = runTest {
        val api =
            api(
                """{"data":{"simulateTransaction":{"effects":{"status":"SUCCESS","executionError":null,
                   "gasEffects":{"gasSummary":{"computationCost":1000,"storageCost":2000,"storageRebate":500}}}}}}"""
            )

        val effects = api.dryRunTransaction("tx-bytes").effects

        assertEquals("success", effects.status.status)
        assertEquals("1000", effects.gasUsed.computationCost)
        assertEquals("2000", effects.gasUsed.storageCost)
        assertEquals("500", effects.gasUsed.storageRebate)
    }

    // A simulation that executes but aborts must surface the abort reason — this is the only
    // pre-signature check that a send will actually succeed on chain.
    @Test
    fun `dryRunTransaction throws the execution error when the simulation fails`() = runTest {
        val api =
            api(
                """{"data":{"simulateTransaction":{"effects":{"status":"FAILURE",
                   "executionError":{"message":"InsufficientGas","abortCode":null,"identifier":null},
                   "gasEffects":{"gasSummary":{"computationCost":0,"storageCost":0,"storageRebate":0}}}}}}"""
            )

        val e = assertFailsWith<Exception> { api.dryRunTransaction("tx-bytes") }
        assertTrue(e.message!!.contains("InsufficientGas"), "message was: ${e.message}")
    }

    // The BCS bytes travel inside a JSON-encoded `sui.rpc.v2.Transaction`; a plain string is
    // rejected by the node with "expected struct sui.rpc.v2.Transaction".
    @Test
    fun `dryRunTransaction wraps the bytes in the gRPC transaction envelope`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            SuiApiImpl(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    """{"data":{"simulateTransaction":{"effects":{"status":"SUCCESS",
                       "gasEffects":{"gasSummary":{"computationCost":1,"storageCost":1,"storageRebate":0}}}}}}""",
                    capture,
                ),
                json,
            )

        api.dryRunTransaction("the-bcs-bytes")

        assertTrue(capture.lastBody.contains("bcs"), capture.lastBody)
        assertTrue(capture.lastBody.contains("the-bcs-bytes"), capture.lastBody)
    }

    // A digest that hasn't landed yet resolves to a null transaction with no errors — a genuine
    // not-found, safe to keep polling.
    @Test
    fun `checkStatus returns null when the transaction is not found`() = runTest {
        val api = api("""{"data":{"transaction":null}}""")

        assertNull(api.checkStatus("digest"))
    }

    @Test
    fun `checkStatus throws SuiRpcException for a node refusal`() = runTest {
        val api = api(errorBody("indexer outage", code = "INTERNAL_SERVER_ERROR"))

        val e = assertFailsWith<SuiRpcException> { api.checkStatus("digest") }
        assertEquals("indexer outage", e.errorMessage)
        assertEquals("INTERNAL_SERVER_ERROR", e.code)
    }

    // GraphQL reports SUCCESS/FAILURE while SuiStatusProvider matches on the lowercase spelling.
    // Without the lowercasing a confirmed transaction reads as pending until the poll times out.
    @Test
    fun `checkStatus lowercases the execution status`() = runTest {
        val api =
            api(
                """{"data":{"transaction":{"digest":"abc","effects":{"status":"SUCCESS",
                   "executionError":null,"checkpoint":{"sequenceNumber":10}}}}}"""
            )

        val response = api.checkStatus("digest")

        assertEquals("abc", response?.digest)
        assertEquals(10L, response?.checkpoint)
        assertEquals("success", response?.effects?.status?.status)
    }

    @Test
    fun `checkStatus carries the execution error on a failed transaction`() = runTest {
        val api =
            api(
                """{"data":{"transaction":{"digest":"abc","effects":{"status":"FAILURE",
                   "executionError":{"message":"MoveAbort"},"checkpoint":{"sequenceNumber":11}}}}}"""
            )

        val status = api.checkStatus("digest")?.effects?.status

        assertEquals("failure", status?.status)
        assertEquals("MoveAbort", status?.error)
    }

    // An executed but not-yet-checkpointed transaction has no sequence number, which the status
    // provider reads as still pending.
    @Test
    fun `checkStatus leaves the checkpoint null until the transaction is checkpointed`() = runTest {
        val api =
            api("""{"data":{"transaction":{"digest":"abc","effects":{"status":"SUCCESS"}}}}""")

        assertNull(api.checkStatus("digest")?.checkpoint)
    }

    @Test
    fun `getLatestCheckpointSequenceNumber returns the sequence number`() = runTest {
        val api = api("""{"data":{"checkpoint":{"sequenceNumber":309001173}}}""")

        assertEquals(309001173L, api.getLatestCheckpointSequenceNumber())
    }

    @Test
    fun `getLatestCheckpointSequenceNumber returns null on a node error`() = runTest {
        val api = api(errorBody("unavailable", code = "INTERNAL_SERVER_ERROR"))

        assertNull(api.getLatestCheckpointSequenceNumber())
    }

    private fun coinsPage(
        hasNextPage: Boolean,
        objectId: String = "0xcoin1",
        endCursor: String = "cursor-1",
        repr: String = "0x2::coin::Coin<0x2::sui::SUI>",
    ) =
        """
        {"data":{"address":{"objects":{
          "pageInfo":{"hasNextPage":$hasNextPage,"endCursor":"$endCursor"},
          "nodes":[{
            "address":"$objectId",
            "version":100,
            "digest":"digest-1",
            "previousTransaction":{"digest":"prev-1"},
            "contents":{"type":{"repr":"$repr"},"json":{"balance":"600"}}
          }]
        }}}}
        """
            .trimIndent()

    private companion object {
        const val COIN_TYPE =
            "0x0a2b3c4d5e6f7809000000000000000000000000000000000000000000000001::gold::GOLD"

        /** The zero-padded `0x2` spelling GraphQL returns for every address in a type. */
        const val PADDED_TWO = "0x0000000000000000000000000000000000000000000000000000000000000002"
    }
}
