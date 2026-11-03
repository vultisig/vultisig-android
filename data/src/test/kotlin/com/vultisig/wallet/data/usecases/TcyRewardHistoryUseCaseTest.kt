package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApiImpl
import com.vultisig.wallet.data.blockchain.thorchain.ThorChainInboundVaultSnapshot
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import java.math.BigInteger
import java.util.Date
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Reads Midgard bodies through the real [ThorChainApiImpl], so decoding is covered as well. */
internal class TcyRewardHistoryUseCaseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun useCase(body: String): TcyRewardHistoryUseCase =
        TcyRewardHistoryUseCaseImpl(
            ThorChainApiImpl(
                httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body),
                thorChainSwapQuoteResponseJsonSerializer = mockk(),
                json = json,
                inboundVaultSnapshot = ThorChainInboundVaultSnapshot(TestTimeSource()),
            )
        )

    @Test
    fun `an address never paid has no history`() = runTest {
        // Live Midgard body for a non-staker (2026-11-03): HTTP 200 with null distributions.
        val body =
            """
            {
              "address": "thor1ls33ayg26kmltw7jjy55p32ghjna09zp74t4az",
              "apr": "0",
              "distributions": null,
              "total": "0"
            }
            """
                .trimIndent()

        useCase(body)(ADDRESS).shouldBeEmpty()
    }

    @Test
    fun `payouts are newest first with seconds read as dates`() = runTest {
        // First three rows of the live body for thor1009p…rzfmp8 (2026-11-03), in Midgard's order.
        val body =
            distributions(
                row(amount = "200000", date = "1792242794"),
                row(amount = "100000", date = "1752453427"),
                row(amount = "100000", date = "1752718199"),
            )

        useCase(body)(ADDRESS) shouldBe
            listOf(
                TcyRewardPayout(Date(1_792_242_794_000L), BigInteger("200000")),
                TcyRewardPayout(Date(1_752_718_199_000L), BigInteger("100000")),
                TcyRewardPayout(Date(1_752_453_427_000L), BigInteger("100000")),
            )
    }

    @Test
    fun `rows with an unparseable or non-positive amount or date are dropped`() = runTest {
        val body =
            distributions(
                row(amount = "100000", date = "1752718199"),
                row(amount = "abc", date = "1752718200"),
                row(amount = "0", date = "1752718201"),
                row(amount = "-5", date = "1752718202"),
                row(amount = "100000", date = "soon"),
                row(amount = "100000", date = "0"),
            )

        useCase(body)(ADDRESS) shouldBe
            listOf(TcyRewardPayout(Date(1_752_718_199_000L), BigInteger("100000")))
    }

    @Test
    fun `the payouts add up to the total Midgard reports`() = runTest {
        val body =
            distributions(
                row(amount = "200000", date = "1792242794"),
                row(amount = "100000", date = "1752718199"),
                row(amount = "100000", date = "1752453427"),
                total = "400000",
            )

        val total = useCase(body)(ADDRESS).fold(BigInteger.ZERO) { acc, it -> acc + it.amount }

        total shouldBe BigInteger("400000")
    }

    private fun row(amount: String, date: String) =
        """{ "amount": "$amount", "date": "$date", "price": "64084712" }"""

    private fun distributions(vararg rows: String, total: String = "0") =
        """
        {
          "address": "$ADDRESS",
          "apr": "0.08755630594585632",
          "distributions": [${rows.joinToString(",")}],
          "total": "$total"
        }
        """
            .trimIndent()

    private companion object {
        const val ADDRESS = "thor1009p387nnfxpren0q46au5gn8y06ngqvrzfmp8"
    }
}
