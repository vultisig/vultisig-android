package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.SplTokenResponseJsonSerializerImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers [SolanaApiImp.getMinimumBalanceForRentExemption] (the no-arg, 165-byte SPL Associated
 * Token Account overload): a transient RPC failure must fall back to the last successfully-fetched
 * value instead of zero, and a cold-start failure (no prior success this session) must fall back to
 * the standard 165-byte rent-exempt minimum (issue #5345).
 */
class SolanaRentExemptionCacheTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun apiRespondingWith(vararg responses: Pair<HttpStatusCode, String>): SolanaApi =
        SolanaApiImp(
            json = json,
            httpClient = MockHttpClient.respondingWithSequence(*responses, jsonFormat = json),
            splTokenSerializer = SplTokenResponseJsonSerializerImpl(json),
        )

    @Test
    fun `returns the live value on a successful fetch`() = runTest {
        val api = apiRespondingWith(HttpStatusCode.OK to rentExemption(2_500_000))

        val result = api.getMinimumBalanceForRentExemption()

        assertEquals(2_500_000.toBigInteger(), result)
    }

    @Test
    fun `falls back to the cached value when a later fetch fails`() = runTest {
        val api =
            apiRespondingWith(
                HttpStatusCode.OK to rentExemption(2_500_000),
                HttpStatusCode.OK to RPC_ERROR,
            )

        val first = api.getMinimumBalanceForRentExemption()
        val second = api.getMinimumBalanceForRentExemption()

        assertEquals(2_500_000.toBigInteger(), first)
        assertEquals(2_500_000.toBigInteger(), second)
    }

    @Test
    fun `falls back to the SPL rent bootstrap constant on a cold-start failure`() = runTest {
        val api = apiRespondingWith(HttpStatusCode.OK to RPC_ERROR)

        val result = api.getMinimumBalanceForRentExemption()

        assertEquals(2_039_280.toBigInteger(), result)
    }

    private fun rentExemption(lamports: Long): String =
        """
        { "result": $lamports }
        """
            .trimIndent()

    private companion object {
        val RPC_ERROR =
            """
            { "error": { "code": -32602, "message": "boom" }, "result": null }
            """
                .trimIndent()
    }
}
