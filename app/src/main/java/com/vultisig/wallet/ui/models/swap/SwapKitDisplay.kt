package com.vultisig.wallet.ui.models.swap

/**
 * `SwapKit` or `SwapKit (<sub-provider>)`. The sub-provider is rendered verbatim from the wire so
 * the label matches what iOS shows for the same payload — `CHAINFLIP` / `NEAR` / `GARDEN`.
 */
internal fun formatSwapKitProviderLabel(subProvider: String?): String =
    if (subProvider.isNullOrBlank()) "SwapKit" else "SwapKit ($subProvider)"
