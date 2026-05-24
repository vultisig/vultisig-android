package com.vultisig.wallet.ui.models.swap

/**
 * Build the SwapKit provider label shown on verify rows and history entries: `SwapKit` when no
 * sub-provider is known, `SwapKit (<sub-provider>)` otherwise. The sub-provider token is rendered
 * **verbatim** from the wire value (`CHAINFLIP`, `NEAR`, `GARDEN`, …) to match iOS'
 * `SwapPayload.providerName` (`SwapPayload.swift:101`), `SwapQuote.displayName`
 * (`SwapQuote.swift:89`), and `TransactionHistoryRecorder.recordSwap`
 * (`TransactionHistoryRecorder.swift:202`) — all three iOS call sites embed the raw
 * `payload.subProvider` directly so cross-platform users see the same label.
 *
 * Lives at app-module scope because both [SwapQuoteManager.fetchSwapKitQuote] (initiator path) and
 * [com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel] (peer-side verify row) render the
 * label, and they must agree byte-for-byte.
 */
internal fun formatSwapKitProviderLabel(subProvider: String?): String =
    if (subProvider.isNullOrBlank()) "SwapKit" else "SwapKit ($subProvider)"
