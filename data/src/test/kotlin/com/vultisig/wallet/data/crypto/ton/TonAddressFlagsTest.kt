package com.vultisig.wallet.data.crypto.ton

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Every vector below is the same account — a STON.fi v2 router — re-encoded with a different tag or
 * spelling, so the reader is pinned on the tag alone and not on anything else that varies between
 * two encodings of one address.
 */
internal class TonAddressFlagsTest {

    @Test
    fun `EQ tag reads as bounceable`() {
        assertEquals(TonBounceability.BOUNCEABLE, TonAddressFlags.bounceabilityOf(EQ))
    }

    @Test
    fun `UQ tag reads as non-bounceable`() {
        assertEquals(TonBounceability.NON_BOUNCEABLE, TonAddressFlags.bounceabilityOf(UQ))
    }

    @Test
    fun `raw form declares no bounceability`() {
        assertEquals(TonBounceability.UNSPECIFIED, TonAddressFlags.bounceabilityOf(RAW))
    }

    @Test
    fun `standard base64 spelling reads the same tag as base64url`() {
        assertEquals(
            TonAddressFlags.bounceabilityOf(EQ_URL_SAFE),
            TonAddressFlags.bounceabilityOf(EQ_STANDARD),
        )
        assertEquals(TonBounceability.BOUNCEABLE, TonAddressFlags.bounceabilityOf(EQ_STANDARD))
        assertEquals(TonBounceability.NON_BOUNCEABLE, TonAddressFlags.bounceabilityOf(UQ_STANDARD))
    }

    @Test
    fun `testnet bit does not hide the bounceable tag`() {
        assertEquals(TonBounceability.BOUNCEABLE, TonAddressFlags.bounceabilityOf(EQ_TESTNET))
        assertEquals(TonBounceability.NON_BOUNCEABLE, TonAddressFlags.bounceabilityOf(UQ_TESTNET))
    }

    @Test
    fun `surrounding whitespace does not change the tag`() {
        assertEquals(TonBounceability.NON_BOUNCEABLE, TonAddressFlags.bounceabilityOf("  $UQ\n"))
    }

    @Test
    fun `unclassifiable input never claims a flag it did not declare`() {
        val unclassifiable =
            listOf(
                "",
                "EQ",
                "not an address at all",
                // Right length, not base64 in either alphabet.
                "!".repeat(48),
                // Valid base64url of 36 bytes, but the tag is neither 0x11 nor 0x51.
                BAD_TAG,
                // An EVM address that happens to start with the same letter as an EQ address.
                "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE",
            )

        unclassifiable.forEach { address ->
            assertEquals(
                TonBounceability.UNSPECIFIED,
                TonAddressFlags.bounceabilityOf(address),
                "expected no flag for: $address",
            )
        }
    }

    private companion object {
        const val EQ = "EQABT9GCyDI60CbC4c6uS33HFDwaqd6MddiwIIw7CXTgNR3A"
        const val UQ = "UQABT9GCyDI60CbC4c6uS33HFDwaqd6MddiwIIw7CXTgNUAF"
        const val RAW = "0:014fd182c8323ad026c2e1ceae4b7dc7143c1aa9de8c75d8b0208c3b0974e035"

        // tag OR'd with the 0x80 testnet-only bit: 0x91 and 0xd1.
        const val EQ_TESTNET = "kQABT9GCyDI60CbC4c6uS33HFDwaqd6MddiwIIw7CXTgNaZK"
        const val UQ_TESTNET = "0QABT9GCyDI60CbC4c6uS33HFDwaqd6MddiwIIw7CXTgNfuP"

        // A second router, whose payload contains a byte that base64url spells `-` and standard
        // base64 spells `+`, so the two spellings are actually distinct strings.
        const val EQ_URL_SAFE = "EQACn16m9OrZ-mw186M4NlIpVP8Tb3q6SV9aX8NjSgVfJTo9"
        const val EQ_STANDARD = "EQACn16m9OrZ+mw186M4NlIpVP8Tb3q6SV9aX8NjSgVfJTo9"
        const val UQ_STANDARD = "UQACn16m9OrZ+mw186M4NlIpVP8Tb3q6SV9aX8NjSgVfJWf4"

        // 36 bytes of base64url whose leading byte is 0x00.
        const val BAD_TAG = "AAABT9GCyDI60CbC4c6uS33HFDwaqd6MddiwIIw7CXTgNR3A"
    }
}
