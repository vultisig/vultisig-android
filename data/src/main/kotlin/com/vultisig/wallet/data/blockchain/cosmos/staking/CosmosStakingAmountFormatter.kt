package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.RoundingMode

/**
 * Shared base-unit conversion for Cosmos-SDK staking flows. The Cosmos proto encoders take amounts
 * as decimal strings in base units (e.g. `"1500000"` for 1.5 LUNA at 6 decimals), so every per-flow
 * builder needs the same human-decimal → base-unit conversion. Factored out so all four flows
 * (delegate, undelegate, redelegate, withdrawRewards) cannot diverge on rounding mode or
 * comma-handling.
 *
 * `RoundingMode.DOWN` mirrors the iOS / SDK encoder so we never silently over-stake when the user
 * types a value with more decimals than the chain accepts.
 *
 * Port of iOS `CosmosStakingAmountFormatter.swift` (vultisig-ios PR #4432).
 */
object CosmosStakingAmountFormatter {

    /**
     * Converts a human-decimal amount string (e.g. `"1.5"` or `"1,5"`) to the chain's base-unit
     * string (e.g. `"1500000"` for 6-decimal LUNA). Returns `"0"` on any parse failure rather than
     * throwing — the downstream form validator surfaces "amount required" instead of a hard error
     * from the builder.
     */
    fun baseUnitsString(amount: String, decimals: Int): String {
        val normalized = amount.replace(',', '.')
        val parsed = normalized.toBigDecimalOrNull() ?: return "0"
        return parsed.movePointRight(decimals).setScale(0, RoundingMode.DOWN).toPlainString()
    }
}
