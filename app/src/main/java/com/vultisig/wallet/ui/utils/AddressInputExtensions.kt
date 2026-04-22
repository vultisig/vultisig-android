package com.vultisig.wallet.ui.utils

/**
 * Returns this user-entered destination address with surrounding whitespace stripped.
 *
 * Pasted addresses commonly carry leading or trailing spaces, tabs, or newlines that must not reach
 * name resolution, validation, or the outgoing transaction payload. Apply this at the boundary
 * before any such use.
 */
internal fun CharSequence.asAddressInput(): String = toString().trim()
