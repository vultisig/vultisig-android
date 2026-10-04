package com.vultisig.wallet.ui.utils

private val URI_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*:(.+)$")

/**
 * Returns this user-entered destination address with surrounding Unicode whitespace stripped and
 * any BIP-21 / EIP-681 URI scheme removed.
 *
 * Pasted addresses commonly carry leading or trailing spaces (including Unicode non-breaking
 * spaces), tabs, or newlines that must not reach name resolution, validation, or the outgoing
 * transaction payload. Apply this at the boundary before any such use.
 *
 * A link that names a contract function (`ethereum:<contract>/transfer?address=…`) is returned
 * whole, so it fails validation: its target is the contract, not the recipient. The send form
 * reads such a link through [asEip681TokenTransfer] instead.
 */
internal fun CharSequence.asAddressInput(): String {
    val trimmed = toString().trimUnicodeWhitespace()
    val match = URI_REGEX.matchEntire(trimmed) ?: return trimmed
    val target = match.groupValues[1].substringBefore('?')
    if ('/' in target) return trimmed
    return target.substringBefore('@').trimUnicodeWhitespace()
}

/** An EIP-681 ERC-20 `transfer` link: [contract] is the token, [recipient] the payee. */
internal data class Eip681TokenTransfer(
    val contract: String,
    val chainId: String?,
    val recipient: String,
)

/**
 * Parses `ethereum:[pay-]<contract>[@<chainId>]/transfer?address=<recipient>[&uint256=<amount>]`,
 * or returns null when this is not such a link.
 */
internal fun CharSequence.asEip681TokenTransfer(): Eip681TokenTransfer? {
    val trimmed = toString().trimUnicodeWhitespace()
    if (!trimmed.startsWith(EIP681_SCHEME, ignoreCase = true)) return null
    val payload = trimmed.substring(EIP681_SCHEME.length).removePrefix("pay-")
    val path = payload.substringBefore('?')
    if (path.substringAfter('/', missingDelimiterValue = "") != "transfer") return null
    val target = path.substringBefore('/')
    val recipient =
        payload
            .substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .firstOrNull { it.startsWith("address=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() } ?: return null
    return Eip681TokenTransfer(
        contract = target.substringBefore('@'),
        chainId = target.substringAfter('@', missingDelimiterValue = "").ifBlank { null },
        recipient = recipient,
    )
}

private const val EIP681_SCHEME = "ethereum:"

private fun String.trimUnicodeWhitespace(): String =
    dropWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
        .dropLastWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
