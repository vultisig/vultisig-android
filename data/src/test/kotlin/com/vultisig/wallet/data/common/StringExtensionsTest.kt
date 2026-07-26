package com.vultisig.wallet.data.common

import io.kotest.matchers.shouldBe
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
}
