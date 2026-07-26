@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.normalizeMessageFormat
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

/**
 * Cross-path invariant for issue #5402: for a well-formed personal_sign message — plain text, or
 * `0x`-prefixed even-length hex — hashing the UTF-8 bytes of whatever [normalizeMessageFormat]
 * displays must reproduce exactly what [SigningHelper] signs. (Malformed hex, e.g. odd length or a
 * non-hex character, falls back to displaying the raw string on both sides of this gate; that
 * fallback is intentionally excluded here since normalizeMessageFormat's odd-length/invalid-hex
 * guards already cover it directly.)
 */
class PersonalSignDisplayMatchesSignedMessageTest {

    @Test
    fun `displayed message hashes to the same bytes SigningHelper signs`() {
        val messages =
            listOf(
                "56756c7469736967", // hex-charset, no 0x prefix -> must be signed as UTF-8 text
                "0x56756c7469736967", // 0x-prefixed hex -> decodes to "Vultisig" for display
                "",
            )

        for (message in messages) {
            val displayed = message.normalizeMessageFormat()
            val expected = listOf(displayed.toByteArray().toKeccak256ByteArray().toHexString())

            val actual =
                SigningHelper.getKeysignMessages(
                    CustomMessagePayload(
                        method = "personal_sign",
                        message = message,
                        chain = "Ethereum",
                    )
                )

            actual shouldBe expected
        }
    }
}
