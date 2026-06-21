package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard

/**
 * Resolves the externally-routed swap recipient surfaced on the verify/keysign screen (issue #4972,
 * parity with vultisig/vultisig-windows#4152).
 *
 * The recipient is read from the bytes that will actually be signed — the THORChain/MayaChain swap
 * memo's `DESTINATION` segment — rather than any live form state, so a device that only joins to
 * co-sign an extension-initiated swap still sees where the funds are going.
 *
 * Returns the recipient only when it differs from the vault's own address on the destination chain;
 * a default own-address swap returns null (no warning row). Comparison is chain-aware:
 * case-insensitive for EVM (a memo may carry a lowercase address while the vault address is
 * checksummed) and exact for case-sensitive formats (Solana base58, UTXO, Cosmos bech32).
 */
internal fun resolveExternalSwapRecipient(
    memo: String?,
    destinationChain: Chain,
    vaultDestinationAddress: String,
): String? {
    val recipient = parseThorchainMemoDestination(memo) ?: return null
    if (recipient.isBlank() || vaultDestinationAddress.isBlank()) return null

    val matchesOwnAddress =
        if (destinationChain.standard == TokenStandard.EVM) {
            recipient.equals(vaultDestinationAddress, ignoreCase = true)
        } else {
            recipient == vaultDestinationAddress
        }

    return recipient.takeUnless { matchesOwnAddress }
}

/**
 * Extracts the `DESTINATION` address from a THORChain/MayaChain swap memo
 * (`SWAP:ASSET:DESTINATION:LIM/…`). `SWAP`, `s` and the `=` shorthand are interchangeable swap
 * verbs; the shorthand also appears in limit (`=<`) and modify-limit (`m=<`) variants, all of which
 * keep the destination at the same segment. The destination always sits at `segments[2]`, so any
 * verb carrying the swap shorthand is accepted (matching the Windows reference, which applies no
 * verb gate). Non-swap memos — liquidity add (`+`), withdraw (`-`), bond, loan, etc. — are rejected
 * so a stray memo never surfaces a phantom recipient. Returns null for any memo that isn't a swap
 * or doesn't carry a destination segment.
 */
internal fun parseThorchainMemoDestination(memo: String?): String? {
    val trimmed = memo?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val segments = trimmed.split(":")
    if (segments.size < 3) return null

    val verb = segments[0].uppercase()
    val isSwap = verb == "SWAP" || verb == "S" || verb.contains("=")
    if (!isSwap) return null

    return segments[2].takeIf { it.isNotBlank() }
}
