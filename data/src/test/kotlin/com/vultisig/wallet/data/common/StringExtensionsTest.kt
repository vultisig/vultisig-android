package com.vultisig.wallet.data.common

import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

class StringExtensionsTest {

    @Test
    fun `isHexPrefixed requires an explicit 0x prefix`() {
        "0x56756c7469736967".isHexPrefixed() shouldBe true
        "56756c7469736967".isHexPrefixed() shouldBe false
        "0X56756c7469736967".isHexPrefixed() shouldBe false
        "Hello Vultisig".isHexPrefixed() shouldBe false
        "".isHexPrefixed() shouldBe false
    }

    @Test
    fun `normalizeMessageFormat leaves a hex-charset message without 0x prefix untouched`() {
        // #5402: must NOT decode to "Vultisig" — SigningHelper hashes this as raw UTF-8 text
        // since it has no 0x prefix, so the Verify screen must display the same literal string.
        val message = "56756c7469736967"

        message.normalizeMessageFormat() shouldBe message
    }

    @Test
    fun `normalizeMessageFormat decodes a proper 0x-prefixed hex message`() {
        "0x56756c7469736967".normalizeMessageFormat() shouldBe "Vultisig"
    }

    @Test
    fun `normalizeMessageFormat leaves odd-length 0x-prefixed hex untouched`() {
        val message = "0xabc"

        message.normalizeMessageFormat() shouldBe message
    }

    @Test
    fun `normalizeMessageFormat leaves 0x-prefixed content with a non-hex character untouched`() {
        // A non-hex character (e.g. "z") after the 0x prefix must not decode: SigningHelper's
        // hex-decode maps invalid digits to garbage bytes rather than failing, so decoding here
        // too could display a plausible-looking string for content that isn't well-formed hex.
        val message = "0x008z41"

        message.normalizeMessageFormat() shouldBe message
    }

    @Test
    fun `convertToBigIntegerOrZero parses an unsigned quantity`() {
        "0x2540be400".convertToBigIntegerOrZero() shouldBe BigInteger("10000000000")
        "2540be400".convertToBigIntegerOrZero() shouldBe BigInteger("10000000000")
        "0x0".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
    }

    @Test
    fun `convertToBigIntegerOrZero rejects a signed quantity`() {
        // BigInteger(String, radix) honours a leading "-", so without a sign floor a hostile RPC
        // could hand back a real negative fee. WalletCore would then encode it as two's-complement
        // bytes and read them back unsigned ("-5" -> 0xfb -> 251), inflating the signed fee.
        "-5".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
        "0x-5".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
        "-2540be400".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
    }

    @Test
    fun `convertToBigIntegerOrZero falls back to zero on malformed input`() {
        null.convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
        "".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
        "0x".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
        "0xzz".convertToBigIntegerOrZero() shouldBe BigInteger.ZERO
    }
}
