package com.vultisig.wallet.ui.utils

private val URI_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*:(.+)$")

/**
 * Returns this user-entered destination address with surrounding Unicode whitespace stripped and
 * any BIP-21 / EIP-681 URI scheme removed.
 *
 * Pasted addresses commonly carry leading or trailing spaces (including Unicode non-breaking
 * spaces), tabs, or newlines that must not reach name resolution, validation, or the outgoing
 * transaction payload. Apply this at the boundary before any such use.
 */
internal fun CharSequence.asAddressInput(): String {
    val trimmed = toString().trimUnicodeWhitespace()
    val match = URI_REGEX.matchEntire(trimmed) ?: return trimmed
    val payload = match.groupValues[1]
    return payload
        .substringBefore('?')
        .substringBefore('@')
        .substringBefore('/')
        .trimUnicodeWhitespace()
}

private fun String.trimUnicodeWhitespace(): String =
    dropWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
        .dropLastWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
