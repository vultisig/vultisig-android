package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializerImpl
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Test

/**
 * Pins the difference between [SolanaApi.getBalance] and [SolanaApi.getBalanceOrNull].
 *
 * `getBalance` answers every failure with zero, which the callers that only display a balance want
 * — but a zero balance is also what an empty wallet looks like. The gates that refuse a transaction
 * the wallet cannot pay for read that zero as "no funds" and refused funded wallets whenever the
 * node hiccupped, so they ask through the nullable overload instead (#5607).
 */
class SolanaBalanceReadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun apiRespondingWith(body: String) =
        SolanaApiImp(
            json = json,
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body, json),
            splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
        )

    private fun apiFailingTransport() =
        SolanaApiImp(
            json = json,
            httpClient = MockHttpClient.throwingIOException(IOException("no route to host")),
            splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
        )

    @Test
    fun `a successful read answers the same lamports either way`() = runTest {
        val body = """{ "result": { "value": 123456789 }, "error": null }"""

        apiRespondingWith(body).getBalanceOrNull(ADDRESS) shouldBe BigInteger("123456789")
        apiRespondingWith(body).getBalance(ADDRESS) shouldBe BigInteger("123456789")
    }

    /** A funded wallet whose node returned a json-rpc error must not read as an empty one. */
    @Test
    fun `a json-rpc error is unknown to getBalanceOrNull and zero to getBalance`() = runTest {
        val body = """{ "result": null, "error": "node is behind by 300 slots" }"""

        apiRespondingWith(body).getBalanceOrNull(ADDRESS).shouldBeNull()
        apiRespondingWith(body).getBalance(ADDRESS) shouldBe BigInteger.ZERO
    }

    /** Same for a node that could not be reached at all. */
    @Test
    fun `an unreachable node is unknown to getBalanceOrNull and zero to getBalance`() = runTest {
        apiFailingTransport().getBalanceOrNull(ADDRESS).shouldBeNull()
        apiFailingTransport().getBalance(ADDRESS) shouldBe BigInteger.ZERO
    }

    /** A response carrying neither an error nor a value states nothing about the balance. */
    @Test
    fun `a resultless response is unknown rather than zero`() = runTest {
        val body = """{ "error": null }"""

        apiRespondingWith(body).getBalanceOrNull(ADDRESS).shouldBeNull()
        apiRespondingWith(body).getBalance(ADDRESS) shouldBe BigInteger.ZERO
    }

    private companion object {
        const val ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
    }
}
