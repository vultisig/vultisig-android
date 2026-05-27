package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson

/**
 * `SwapKit` or `SwapKit (<sub-provider>)`. The sub-provider is rendered verbatim from the wire so
 * the label matches what iOS shows for the same payload — `CHAINFLIP` / `NEAR` / `GARDEN`.
 */
internal fun formatSwapKitProviderLabel(subProvider: String?): String =
    if (subProvider.isNullOrBlank()) "SwapKit" else "SwapKit ($subProvider)"

/**
 * ERC-20 `approve` spender for an EVM swap. SwapKit pulls the sell token through a dedicated
 * token-transfer proxy reported as `tx.allowanceTarget`, which is distinct from the swap entry
 * contract `tx.to`. 1inch / Kyber / LiFi leave `allowanceTarget` null (their router is itself the
 * spender), so they fall back to `to`. This distinction is fund-critical: approving `to` for a
 * SwapKit route reverts with `ERC20InsufficientAllowance`, so the derivation is factored out here
 * and pinned by a test rather than living inline in the swap-build flow.
 */
internal fun approveSpenderFor(tx: OneInchSwapTxJson): String = tx.allowanceTarget ?: tx.to
