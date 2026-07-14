package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Behavioural tests for [RippleApiImp.getBalance] around the failure vs. empty-account distinction
 * (issue #5276): a network failure must propagate instead of being swallowed into a zero balance,
 * while a genuinely unfunded account still resolves to zero without throwing.
 */
class RippleApiBalanceTest {

    private val rippleCoin =
        Coin.EMPTY.copy(
            chain = Chain.Ripple,
            ticker = "XRP",
            address = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh",
            decimal = 6,
            isNativeToken = true,
        )

    // A funded account nets the live balance against the base + owner-count reserves.
    @Test
    fun `getBalance returns balance minus reserves for a funded account`() = runBlocking {
        val body =
            """
            {
              "result": {
                "account_data": { "Balance": "99000000", "OwnerCount": 3 },
                "state": {
                  "validated_ledger": {
                    "reserve_base": 10000000,
                    "reserve_inc": 2000000,
                    "base_fee": 10
                  },
                  "load_base": 256,
                  "load_factor": 256
                }
              }
            }
            """
                .trimIndent()
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        // 99000000 - (10000000 + 3 * 2000000) = 83000000
        assertEquals(BigInteger("83000000"), api.getBalance(rippleCoin))
    }

    // An unfunded account (actNotFound) returns HTTP 200 with a null account_data, which must still
    // resolve to a real zero — this is the one legitimate zero, distinct from a network failure.
    @Test
    fun `getBalance returns zero for an unfunded account without throwing`() = runBlocking {
        val body =
            """
            {
              "result": {
                "state": {
                  "validated_ledger": {
                    "reserve_base": 10000000,
                    "reserve_inc": 2000000,
                    "base_fee": 10
                  },
                  "load_base": 256,
                  "load_factor": 256
                }
              }
            }
            """
                .trimIndent()
        val api = RippleApiImp(MockHttpClient.respondingWith(HttpStatusCode.OK, body))

        assertEquals(BigInteger.ZERO, api.getBalance(rippleCoin))
    }

    // The core of #5276: a socket timeout must surface as a typed, classifiable failure so the
    // balance layer keeps the last-known value / shows a loading state instead of committing a
    // fake $0.00 that looks like the funds disappeared.
    @Test
    fun `getBalance propagates a timeout instead of swallowing it into zero`() {
        val api =
            RippleApiImp(MockHttpClient.throwingIOException(SocketTimeoutException("timeout")))

        val error = assertThrows<NetworkException> { runBlocking { api.getBalance(rippleCoin) } }

        assertEquals(NetworkErrorKind.Timeout, error.kind)
    }

    // fetchAccountsInfo must rethrow the original transport exception (not flatten it into a
    // generic IllegalStateException) so the timeout/offline cause survives for classification.
    @Test
    fun `fetchAccountsInfo rethrows the original network exception preserving its kind`() {
        val api =
            RippleApiImp(MockHttpClient.throwingIOException(SocketTimeoutException("timeout")))

        val error =
            assertThrows<NetworkException> {
                runBlocking { api.fetchAccountsInfo(rippleCoin.address) }
            }

        assertEquals(NetworkErrorKind.Timeout, error.kind)
    }
}
