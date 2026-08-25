package com.vultisig.wallet.data.crypto.ton

import java.util.Base64

/** Bounceability a TON destination address declares, or [UNSPECIFIED] when it declares none. */
enum class TonBounceability {
    /** User-friendly `EQ…` form — a message the destination rejects is refunded. */
    BOUNCEABLE,
    /** User-friendly `UQ…` form — a message the destination rejects is absorbed. */
    NON_BOUNCEABLE,
    /** Raw `0:hex` form, or anything this reader cannot classify. Carries no flag at all. */
    UNSPECIFIED,
}

/**
 * Reads the bounceable tag out of a TON address rather than inferring it from the leading
 * character.
 *
 * A user-friendly address is base64 of `tag(1) | workchain(1) | hash(32) | crc16(2)`, where the tag
 * is `0x11` bounceable or `0x51` non-bounceable, optionally OR'd with the `0x80` testnet-only bit.
 * The raw `0:hex` spelling of the same account carries no tag, which is the case a prefix check
 * cannot express: it is neither bounceable nor non-bounceable, and the caller has to decide.
 *
 * Structural validity beyond the length and the tag (the CRC in particular) is not re-checked here
 * — a destination reaches this reader only after `ChainAccountAddressRepository.isValid`, and an
 * unrecognized input degrades to [TonBounceability.UNSPECIFIED] rather than to a flag it did not
 * declare. Pure JVM (no WalletCore) so it stays unit testable.
 */
object TonAddressFlags {

    private const val USER_FRIENDLY_LENGTH = 48
    private const val USER_FRIENDLY_SIZE = 36
    private const val TAG_MASK = 0x7f
    private const val TAG_BOUNCEABLE = 0x11
    private const val TAG_NON_BOUNCEABLE = 0x51

    fun bounceabilityOf(address: String): TonBounceability {
        val trimmed = address.trim()
        if (trimmed.length != USER_FRIENDLY_LENGTH) return TonBounceability.UNSPECIFIED

        val bytes = decodeBase64(trimmed) ?: return TonBounceability.UNSPECIFIED
        if (bytes.size != USER_FRIENDLY_SIZE) return TonBounceability.UNSPECIFIED

        return when (bytes[0].toInt() and TAG_MASK) {
            TAG_BOUNCEABLE -> TonBounceability.BOUNCEABLE
            TAG_NON_BOUNCEABLE -> TonBounceability.NON_BOUNCEABLE
            else -> TonBounceability.UNSPECIFIED
        }
    }

    /**
     * TON friendly addresses appear in both base64url (`-_`) and standard base64 (`+/`) spelling.
     */
    private fun decodeBase64(value: String): ByteArray? =
        runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
            ?: runCatching { Base64.getDecoder().decode(value) }.getOrNull()
}
