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
 * An EIP-681 `transfer` link resolves to its `address=` payee, not the contract. The send form
 * reads such a link through [asEip681TokenTransfer] first, to also check it names the token sent.
 */
internal fun CharSequence.asAddressInput(): String {
    val trimmed = toString().trimUnicodeWhitespace()
    val match = URI_REGEX.matchEntire(trimmed) ?: return trimmed
    val scheme = trimmed.substringBefore(':')
    val payload = match.groupValues[1]
    val beforeQuery = payload.substringBefore('?')

    // EIP-681 function-call form: `<target>[@chainId]/<function>?<params>`. Here `<target>` is the
    // token CONTRACT, not the recipient — for an ERC-20 `transfer` the recipient is the `address`
    // parameter. The old code stripped everything after `/`, so it returned the contract address;
    // sending the selected token to its own contract loses the funds. Parse the function form so
    // the recipient is the real payee, and never silently resolve a non-transfer call to the
    // contract (return the unresolved text, which fails address validation).
    //
    // Gate this on the `ethereum:` scheme — EIP-681 is only defined for it — so a non-Ethereum URI
    // with a path (e.g. `https://x/transfer?address=…`) can't have its `address` query taken as the
    // recipient.
    if (scheme.equals("ethereum", ignoreCase = true) && beforeQuery.contains('/')) {
        val function = beforeQuery.substringAfterLast('/').trimUnicodeWhitespace()
        if (!function.equals("transfer", ignoreCase = true)) {
            return beforeQuery.trimUnicodeWhitespace()
        }
        val recipient = payload.eip681Param("address")
        return recipient?.takeIf { it.isNotEmpty() } ?: beforeQuery.trimUnicodeWhitespace()
    }

    // Plain payment form: `<target>[@chainId]`; the target is the recipient. Any path on a
    // non-Ethereum scheme is stripped (unchanged from the original behaviour).
    return beforeQuery.substringBefore('@').substringBefore('/').trimUnicodeWhitespace()
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
