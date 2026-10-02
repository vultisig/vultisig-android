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

    // EIP-681 function-call form: `<target>[@chainId]/<function>?<params>`. Here `<target>` is the
    // token CONTRACT, not the recipient — for an ERC-20 `transfer` the recipient is the `address`
    // parameter. The old code stripped everything after `/`, so it returned the contract address;
    // sending the selected token to its own contract loses the funds. Parse the function form so
    // the recipient is the real payee, and never silently resolve a non-transfer call to the
    // contract (return the unresolved text, which fails address validation).
    val beforeQuery = payload.substringBefore('?')
    if (beforeQuery.contains('/')) {
        val function = beforeQuery.substringAfterLast('/').trimUnicodeWhitespace()
        if (!function.equals("transfer", ignoreCase = true)) {
            return beforeQuery.trimUnicodeWhitespace()
        }
        val recipient = payload.eip681Param("address")
        return recipient?.takeIf { it.isNotEmpty() } ?: beforeQuery.trimUnicodeWhitespace()
    }

    // Plain payment form: `<target>[@chainId]`; the target is the recipient.
    return beforeQuery.substringBefore('@').trimUnicodeWhitespace()
}

/** Value of an EIP-681 query parameter (after `?`, `&`-separated `key=value`), or null. */
private fun String.eip681Param(key: String): String? =
    substringAfter('?', "")
        .split('&')
        .firstNotNullOfOrNull { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq).trim() == key) {
                pair.substring(eq + 1).trimUnicodeWhitespace()
            } else {
                null
            }
        }

private fun String.trimUnicodeWhitespace(): String =
    dropWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
        .dropLastWhile { Character.isWhitespace(it) || Character.isSpaceChar(it) }
