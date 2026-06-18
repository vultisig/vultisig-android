package com.vultisig.wallet.data.utils

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** Pins the cause-chain classification used to pick a cause-specific keygen error (issue #4956). */
class NetworkErrorKindTest {

    @Test
    fun `directNetworkException returnsItsKind`() {
        val ex = NetworkException(0, "Connection timed out", NetworkErrorKind.Timeout)
        assertEquals(NetworkErrorKind.Timeout, ex.networkErrorKind())
    }

    @Test
    fun `wrappedNetworkException isFoundThroughCauseChain`() {
        val root = NetworkException(0, "No internet connection", NetworkErrorKind.NoConnectivity)
        val wrapped = IllegalStateException("start failed", RuntimeException(root))
        assertEquals(NetworkErrorKind.NoConnectivity, wrapped.networkErrorKind())
    }

    @Test
    fun `rawSocketTimeout isClassifiedAsTimeout`() {
        val ex = RuntimeException("boom", SocketTimeoutException("timeout"))
        assertEquals(NetworkErrorKind.Timeout, ex.networkErrorKind())
    }

    @Test
    fun `rawUnknownHost isClassifiedAsNoConnectivity`() {
        assertEquals(
            NetworkErrorKind.NoConnectivity,
            UnknownHostException("dns").networkErrorKind(),
        )
    }

    @Test
    fun `httpServerError isNotTreatedAsTransportFailure`() {
        // A 500 from the server is an Http-kind NetworkException; the keygen UI keeps the generic
        // message for it rather than claiming a connectivity problem.
        val ex = NetworkException(500, "Internal Server Error")
        assertNull(ex.networkErrorKind())
    }

    @Test
    fun `nonNetworkError returnsNull`() {
        assertNull(IllegalArgumentException("bad arg").networkErrorKind())
        assertNull(IOException("disk").networkErrorKind())
    }
}
