package com.vultisig.wallet.data.crypto.ton

import java.util.Base64
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * Guards the hardcoded swap-router allow-list. Replaces iOS's DEBUG-only `assertionFailure` with a
 * CI check: every literal must be a structurally valid user-friendly TON address (48-char base64url
 * decoding to 36 bytes with a correct CRC16 and a bounceable/non-bounceable tag), and each set must
 * be non-empty so a botched re-sync that empties a set fails the build rather than silently
 * disabling swap classification. Validation is pure-JVM (no WalletCore) so it runs as a unit test.
 */
internal class TonKnownRoutersTest {

    @Test
    fun `every router literal is a valid user-friendly TON address`() {
        val all =
            TonKnownRouters.stonfiV2Routers +
                TonKnownRouters.stonfiV2PtonWallets +
                TonKnownRouters.dedustNativeVaults
        assertAll(
            all.map { address -> { assertTrue(isValidUserFriendly(address), "invalid: $address") } }
        )
    }

    @Test
    fun `router sets are non-empty`() {
        assertTrue(TonKnownRouters.stonfiV2Routers.isNotEmpty())
        assertTrue(TonKnownRouters.stonfiV2PtonWallets.isNotEmpty())
        assertTrue(TonKnownRouters.dedustNativeVaults.isNotEmpty())
    }

    private fun isValidUserFriendly(address: String): Boolean {
        if (address.length != 48) return false
        val bytes =
            runCatching { Base64.getUrlDecoder().decode(address) }.getOrNull() ?: return false
        if (bytes.size != 36) return false
        // tag: 0x11 bounceable / 0x51 non-bounceable, optionally OR'd with the 0x80 testnet bit.
        if ((bytes[0].toInt() and 0x7f) != 0x11 && (bytes[0].toInt() and 0x7f) != 0x51) return false
        val expected = ((bytes[34].toInt() and 0xff) shl 8) or (bytes[35].toInt() and 0xff)
        return crc16(bytes, 34) == expected
    }

    /** CRC16-CCITT (XMODEM): poly 0x1021, init 0 — the checksum TON uses for friendly addresses. */
    private fun crc16(data: ByteArray, length: Int): Int {
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor ((data[i].toInt() and 0xff) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xffff
            }
        }
        return crc
    }
}
