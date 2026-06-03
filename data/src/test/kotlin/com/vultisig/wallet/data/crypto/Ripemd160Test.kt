@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins [Ripemd160] against the published RIPEMD-160 test vectors. */
class Ripemd160Test {

    @Test
    fun `matches the known RIPEMD-160 vectors`() {
        assertEquals(
            "9c1185a5c5e9fc54612808977ee8f548b2258d31",
            Ripemd160.hash("".toByteArray()).toHexString(),
        )
        assertEquals(
            "8eb208f7e05d987a9b044a8e98c6b087f15a0bfc",
            Ripemd160.hash("abc".toByteArray()).toHexString(),
        )
        assertEquals(
            "5d0689ef49d2fae572b881b123a85ffa21595f36",
            Ripemd160.hash("message digest".toByteArray()).toHexString(),
        )
    }
}
