package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Parsing tests for the `account_lines` read that backs XRP trust-line token balances
 * (issue #5210): line extraction, marker pagination, and the per-token balance lookup.
 */
class RippleAccountLinesTest {

    private val issuer = "rvYAfWj5gh67oV6fW32ZzP3Aw4Eubs59B"

    private fun tokenCoin(currency: String = "USD", issuerAddress: String = issuer) =
        Coin.EMPTY.copy(
            chain = Chain.Ripple,
            ticker = currency,
            address = ACCOUNT,
            decimal = RIPPLE_TOKEN_DECIMALS,
            contractAddress = "$currency.$issuerAddress",
            isNativeToken = false,
        )

    private fun linesBody(vararg lines: String, marker: String? = null): String {
        val markerField = marker?.let { ""","marker": "$it"""" } ?: ""
        return """{"result": {"lines": [${lines.joinToString(",")}]$markerField}}"""
    }

    private fun line(currency: String, account: String, balance: String) =
        """{"account": "$account", "currency": "$currency", "balance": "$balance", "limit": "1000000000"}"""

    @Test
    fun `fetchAccountLines parses currency issuer and balance`() = runBlocking {
        val body = linesBody(line("USD", issuer, "125.5"), line("EUR", OTHER_ISSUER, "0.25"))
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        val lines = api.fetchAccountLines(ACCOUNT)

        assertEquals(2, lines.size)
        assertEquals("USD", lines[0].currency)
        assertEquals(issuer, lines[0].account)
        assertEquals("125.5", lines[0].balance)
        assertEquals(OTHER_ISSUER, lines[1].account)
    }

    // An unfunded account answers actNotFound with no `lines` array — an empty holding, not a
    // parse failure.
    @Test
    fun `fetchAccountLines returns empty for an account with no trust lines`() = runBlocking {
        val body = """{"result": {"account": "$ACCOUNT", "error": "actNotFound"}}"""
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        assertTrue(api.fetchAccountLines(ACCOUNT).isEmpty())
    }

    @Test
    fun `fetchAccountLines follows the pagination marker across pages`() = runBlocking {
        val api =
            RippleApiImp(
                MockHttpClient.respondingWithSequence(
                    HttpStatusCode.OK to linesBody(line("USD", issuer, "1"), marker = "page-2"),
                    HttpStatusCode.OK to linesBody(line("EUR", OTHER_ISSUER, "2")),
                )
            )

        val lines = api.fetchAccountLines(ACCOUNT)

        assertEquals(listOf("USD", "EUR"), lines.map { it.currency })
    }

    // A node that echoes the same marker forever would otherwise page until the hard cap; stopping
    // on a repeat keeps a single refresh from burning 25 round-trips.
    @Test
    fun `fetchAccountLines stops when a node repeats the same marker`() = runBlocking {
        val repeating = linesBody(line("USD", issuer, "1"), marker = "stuck")
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, repeating))

        val lines = api.fetchAccountLines(ACCOUNT)

        assertEquals(2, lines.size)
    }

    @Test
    fun `getTokenBalance scales the matching line to token units`() = runBlocking {
        val body = linesBody(line("EUR", issuer, "5"), line("USD", issuer, "125.5"))
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        // 125.5 at 15 decimals
        assertEquals(BigInteger("125500000000000000"), api.getTokenBalance(tokenCoin()))
    }

    // Same currency code, different issuer: the pair must match, or the wallet would show one
    // issuer's balance under another's row.
    @Test
    fun `getTokenBalance ignores a same-currency line from a different issuer`() = runBlocking {
        val body = linesBody(line("USD", OTHER_ISSUER, "999"))
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        assertEquals(BigInteger.ZERO, api.getTokenBalance(tokenCoin()))
    }

    // A negative balance means the account issues the line rather than holding it.
    @Test
    fun `getTokenBalance clamps an owed line to zero`() = runBlocking {
        val body = linesBody(line("USD", issuer, "-42.5"))
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        assertEquals(BigInteger.ZERO, api.getTokenBalance(tokenCoin()))
    }

    @Test
    fun `getTokenBalance returns zero when the account holds no such line`() = runBlocking {
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, linesBody()))

        assertEquals(BigInteger.ZERO, api.getTokenBalance(tokenCoin()))
    }

    // A malformed contract address has no currency/issuer pair to match on; it must resolve to
    // zero rather than matching an arbitrary line.
    @Test
    fun `getTokenBalance returns zero for a coin with no issuer in its contract address`() =
        runBlocking {
            val body = linesBody(line("USD", issuer, "10"))
            val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))
            val malformed = tokenCoin().copy(contractAddress = "USD")

            assertEquals(BigInteger.ZERO, api.getTokenBalance(malformed))
        }

    // The balance layer asks per token, so a vault holding several issued currencies would
    // otherwise re-read the whole trust-line set once per token. Seeing the first response's value
    // on the second call proves the read was coalesced rather than repeated.
    @Test
    fun `repeated reads within the cache window reuse one network response`() = runBlocking {
        val api =
            RippleApiImp(
                MockHttpClient.respondingWithSequence(
                    HttpStatusCode.OK to linesBody(line("USD", issuer, "10")),
                    HttpStatusCode.OK to linesBody(line("USD", issuer, "99")),
                )
            )

        val first = api.getTokenBalance(tokenCoin())
        val second = api.getTokenBalance(tokenCoin())

        assertEquals(BigInteger("10000000000000000"), first)
        assertEquals(first, second)
    }

    @Test
    fun `the request carries the account and resumes from the marker`() = runBlocking {
        val capture = MockHttpClient.RequestCapture()
        val api =
            RippleApiImp(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    linesBody(line("USD", issuer, "1"), marker = "page-2"),
                    capture,
                )
            )

        api.fetchAccountLines(ACCOUNT)

        val lastBody = capture.lastBody.orEmpty()
        assertTrue(lastBody.contains(ACCOUNT), "request must name the account: $lastBody")
        assertTrue(
            lastBody.contains("page-2"),
            "second page must resume from the marker: $lastBody",
        )
    }

    private companion object {
        const val ACCOUNT = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
        const val OTHER_ISSUER = "rcoef87SYMJ58NAFx7fNM5frVknmvHsvJ"
    }
}
