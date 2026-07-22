package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializerImpl
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [SolanaApiImp.getTokenAssociatedAccountByOwner], focusing on the direct-derivation
 * fallback that runs when the primary `getTokenAccountsByOwner` lookup comes back empty or fails
 * (issue #5224). The WalletCore ATA derivation is injected as a fake that records its calls, so the
 * tests assert both the returned pair and whether the fallback was reached — without the native
 * library.
 */
class SolanaAssociatedTokenAccountTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    /** Records every (owner, mint, token2022) the fallback derives, in order. */
    private val derivationCalls = mutableListOf<Triple<String, String, Boolean>>()

    private val fakeDeriver: (String, String, Boolean) -> String? = { owner, mint, token2022 ->
        derivationCalls += Triple(owner, mint, token2022)
        if (token2022) TOKEN_2022_ATA else CLASSIC_ATA
    }

    private fun apiRespondingWith(vararg responses: Pair<HttpStatusCode, String>): SolanaApi =
        SolanaApiImp(
            json = json,
            httpClient = MockHttpClient.respondingWithSequence(*responses),
            splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
            deriveAssociatedTokenAddress = fakeDeriver,
        )

    @Test
    fun `primary lookup success returns the indexed account without a fallback`() = runTest {
        val api = apiRespondingWith(HttpStatusCode.OK to tokenAccounts(INDEXED_ATA, SPL_PROGRAM))

        val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

        assertEquals(INDEXED_ATA to false, result)
        assertTrue(derivationCalls.isEmpty(), "happy path must not derive")
    }

    @Test
    fun `primary lookup flags a Token-2022 account by its owning program`() = runTest {
        val api =
            apiRespondingWith(HttpStatusCode.OK to tokenAccounts(INDEXED_ATA, TOKEN_2022_PROGRAM))

        val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

        assertEquals(INDEXED_ATA to true, result)
        assertTrue(derivationCalls.isEmpty(), "happy path must not derive")
    }

    @Test
    fun `empty primary lookup falls back to the derived classic ATA`() = runTest {
        val api =
            apiRespondingWith(
                HttpStatusCode.OK to NO_TOKEN_ACCOUNTS,
                HttpStatusCode.OK to accountInfo(owner = SPL_PROGRAM),
            )

        val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

        assertEquals(CLASSIC_ATA to false, result)
        // Classic is derived and confirmed first, so Token-2022 is never derived.
        assertEquals(listOf(Triple(OWNER, MINT, false)), derivationCalls)
    }

    @Test
    fun `empty primary lookup falls back to the derived Token-2022 ATA when classic is absent`() =
        runTest {
            val api =
                apiRespondingWith(
                    HttpStatusCode.OK to NO_TOKEN_ACCOUNTS,
                    HttpStatusCode.OK to ACCOUNT_ABSENT,
                    HttpStatusCode.OK to accountInfo(owner = TOKEN_2022_PROGRAM),
                )

            val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

            assertEquals(TOKEN_2022_ATA to true, result)
            assertEquals(
                listOf(Triple(OWNER, MINT, false), Triple(OWNER, MINT, true)),
                derivationCalls,
            )
        }

    @Test
    fun `empty primary lookup with neither derived ATA present reports no account`() = runTest {
        val api =
            apiRespondingWith(
                HttpStatusCode.OK to NO_TOKEN_ACCOUNTS,
                // Pinned as the response for both the classic and Token-2022 probes.
                HttpStatusCode.OK to ACCOUNT_ABSENT,
            )

        val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

        assertEquals(null to false, result)
        assertEquals(listOf(Triple(OWNER, MINT, false), Triple(OWNER, MINT, true)), derivationCalls)
    }

    @Test
    fun `json-rpc error on the primary lookup still runs the derivation fallback`() = runTest {
        val api =
            apiRespondingWith(
                HttpStatusCode.OK to TOKEN_ACCOUNTS_RPC_ERROR,
                HttpStatusCode.OK to accountInfo(owner = SPL_PROGRAM),
            )

        val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

        assertEquals(CLASSIC_ATA to false, result)
        assertEquals(listOf(Triple(OWNER, MINT, false)), derivationCalls)
    }

    @Test
    fun `transport failure on the primary lookup falls back and reports no account gracefully`() =
        runTest {
            // Both the primary lookup and the fallback probes hit the failing transport, so the
            // method must not propagate the error — it reports "no account" after trying to derive.
            val api =
                SolanaApiImp(
                    json = json,
                    httpClient = MockHttpClient.throwingIOException(IOException("boom")),
                    splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
                    deriveAssociatedTokenAddress = fakeDeriver,
                )

            val result = api.getTokenAssociatedAccountByOwner(OWNER, MINT)

            assertEquals(null to false, result)
            assertTrue(derivationCalls.isNotEmpty(), "fallback must be attempted on a throw")
        }

    @Test
    fun `cancellation on the primary lookup propagates and skips the fallback`() = runTest {
        val api =
            SolanaApiImp(
                json = json,
                httpClient = MockHttpClient.throwing(CancellationException("cancelled")),
                splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
                deriveAssociatedTokenAddress = fakeDeriver,
            )

        val error =
            runCatching { api.getTokenAssociatedAccountByOwner(OWNER, MINT) }.exceptionOrNull()

        assertInstanceOf(CancellationException::class.java, error)
        assertTrue(derivationCalls.isEmpty(), "cancellation must short-circuit before the fallback")
    }

    private fun tokenAccounts(pubKey: String, owner: String): String =
        """
        {
          "error": null,
          "result": {
            "value": [
              {
                "pubkey": "$pubKey",
                "account": {
                  "data": { "parsed": { "info": { "tokenAmount": { "amount": "1" } } } },
                  "owner": "$owner"
                }
              }
            ]
          }
        }
        """
            .trimIndent()

    private fun accountInfo(owner: String): String =
        """
        {
          "result": { "value": { "owner": "$owner" } },
          "error": null
        }
        """
            .trimIndent()

    private companion object {
        const val OWNER = "OwnerWallet11111111111111111111111111111111"
        const val MINT = "MintAddress111111111111111111111111111111111"
        const val INDEXED_ATA = "IndexedAta1111111111111111111111111111111111"
        const val CLASSIC_ATA = "ClassicAta1111111111111111111111111111111111"
        const val TOKEN_2022_ATA = "Token2022Ata11111111111111111111111111111111"
        const val SPL_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

        val NO_TOKEN_ACCOUNTS =
            """
            { "error": null, "result": { "value": [] } }
            """
                .trimIndent()

        val TOKEN_ACCOUNTS_RPC_ERROR =
            """
            { "error": { "message": "indexer unavailable" }, "result": null }
            """
                .trimIndent()

        val ACCOUNT_ABSENT =
            """
            { "result": { "value": null }, "error": null }
            """
                .trimIndent()
    }
}
