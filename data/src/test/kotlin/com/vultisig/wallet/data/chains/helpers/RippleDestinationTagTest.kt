package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RippleDestinationTagTest {

    @Test
    fun `canonical destination tag parsing`() {
        assertEquals(1u, RippleDestinationTag.parseCanonicalDestinationTag("1"))
        assertEquals(12345u, RippleDestinationTag.parseCanonicalDestinationTag("12345"))
        assertEquals(
            4_294_967_295u,
            RippleDestinationTag.parseCanonicalDestinationTag("4294967295"),
        )
        // rejected
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("0"))
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag(""))
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("007")) // leading zero
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("4294967296")) // > u32
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("12.5"))
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("abc"))
        assertNull(RippleDestinationTag.parseCanonicalDestinationTag("-1"))
    }

    @Test
    fun `decode X-address canonical vectors`() {
        // Same account, with tag 1 vs without a tag.
        RippleDestinationTag.decodeXAddress("X7AcgcsBL6XDcUb289X4mJ8djcdyKaGZMhc9YTE92ehJ2Fu")!!
            .let {
                assertEquals("r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59", it.classicAddress)
                assertEquals(1u, it.tag)
            }
        RippleDestinationTag.decodeXAddress("X7AcgcsBL6XDcUb289X4mJ8djcdyKaB5hJDWMArnXr61cqZ")!!
            .let {
                assertEquals("r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59", it.classicAddress)
                assertNull(it.tag)
            }
        // uint32-max tag.
        RippleDestinationTag.decodeXAddress("XVLhHMPHU98es4dbozjVtdWzVrDjtV18pX8yuPT7y4xaEHi")!!
            .let {
                assertEquals("rGWrZyQqhTp9Xu7G5Pkayo7bXjH4k4QYpf", it.classicAddress)
                assertEquals(4_294_967_295u, it.tag)
            }
    }

    @Test
    fun `reject invalid X-addresses`() {
        // testnet (starts with T)
        assertNull(
            RippleDestinationTag.decodeXAddress("T7oKJ3q7s94kDH6tpkBowhetT1JKfcfdSCmAXbS75iATyLD")
        )
        // flag-1 tag-0 X-address rejected outright
        assertNull(
            RippleDestinationTag.decodeXAddress("XVLhHMPHU98es4dbozjVtdWzVrDjtV8AqEL4xcZj5whKbmc")
        )
        // corrupt checksum
        assertNull(
            RippleDestinationTag.decodeXAddress("X7AcgcsBL6XDcUb289X4mJ8djcdyKaGZMhc9YTE92ehJ2Fx")
        )
        // not an X-address
        assertNull(RippleDestinationTag.decodeXAddress("r9cZA1mLK5R5Am25ArfXFmqgNwjZgnfk59"))
    }
}
