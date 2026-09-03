package com.vultisig.wallet.ui.models

import androidx.annotation.StringRes
import com.vultisig.wallet.R
import java.util.Locale

/**
 * Actionable copy for an on-chain failure the app recognises from the reason stored against the
 * transaction.
 *
 * A revert reason is router text, not user copy: "Insufficient output" describes a min-output check
 * to whoever wrote the router, and describes nothing at all to the person who just paid gas for it.
 * Matching the reason to an explanation is what lets history say the price moved and the slippage
 * tolerance needs raising, instead of leaving a retry — and another gas payment — as the only
 * obvious move (#5802).
 *
 * A reason that matches nothing maps to null and stays out of the UI. Everything the wallet stores
 * in that field is internal text ("Transaction reverted", "SwapKit failed: reverted", a raw router
 * string), so showing it unrecognised would trade one unhelpful message for another.
 */
enum class TransactionFailureExplanation(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    private val signatures: List<String>,
) {
    /**
     * An aggregator's minimum-output check rejected the swap: between the quote being signed and
     * the transaction landing, the price moved far enough that the payout would have fallen below
     * the floor built into the calldata.
     *
     * The first two signatures are the ones the receipts in #5802 carry (LI.FI and 1inch/Kyber);
     * the rest are the same check worded by other routers.
     */
    MIN_OUTPUT_SLIPPAGE(
        labelRes = R.string.transaction_status_failed_slippage_label,
        descriptionRes = R.string.transaction_status_failed_slippage_description,
        signatures =
            listOf(
                "insufficient output",
                "return amount is not enough",
                "insufficient_output_amount",
                "too little received",
            ),
    );

    companion object {
        /**
         * The explanation for [rawReason], or null when nothing recognises it.
         *
         * Matched as a case-insensitive substring: nodes wrap the reason in their own prose
         * ("execution reverted: Insufficient output"), and routers prefix theirs with a contract
         * name ("UniswapV2Router: INSUFFICIENT_OUTPUT_AMOUNT").
         */
        fun from(rawReason: String?): TransactionFailureExplanation? {
            val normalized =
                rawReason?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { explanation ->
                explanation.signatures.any { it in normalized }
            }
        }
    }
}
