package com.vultisig.wallet.ui.models.transaction

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.TransactionFailureExplanation
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pins which stored failure reasons history is willing to explain (#5802).
 *
 * The two signatures from the issue's receipts must be recognised through whatever wrapping the
 * node or router put around them, and everything else must stay unrecognised — the reason column
 * also holds the app's own internal text, which is no more useful to a user than "Error" is.
 */
class TransactionFailureExplanationTest {

    @Test
    fun `recognises the LI_FI min-output revert`() {
        TransactionFailureExplanation.from("Insufficient output") shouldBe
            TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE
    }

    @Test
    fun `recognises the 1inch and Kyber min-return revert`() {
        TransactionFailureExplanation.from("Return amount is not enough") shouldBe
            TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE
    }

    @Test
    fun `matches through node wrapping and casing`() {
        TransactionFailureExplanation.from("execution reverted: INSUFFICIENT OUTPUT") shouldBe
            TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE
        TransactionFailureExplanation.from(
            "RPC error: Return Amount Is Not Enough [code 3]"
        ) shouldBe TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE
    }

    @Test
    fun `matches a router that prefixes its own contract name`() {
        TransactionFailureExplanation.from("UniswapV2Router: INSUFFICIENT_OUTPUT_AMOUNT") shouldBe
            TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE
    }

    @Test
    fun `the explanation points at the slippage copy`() {
        with(TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE) {
            labelRes shouldBe R.string.transaction_status_failed_slippage_label
            descriptionRes shouldBe R.string.transaction_status_failed_slippage_description
        }
    }

    /**
     * These are the strings the wallet itself stores when it cannot learn a reason. Explaining them
     * would be inventing a cause, so they stay unrecognised and the row keeps saying only "Error".
     */
    @Test
    fun `the app's own placeholder reasons stay unexplained`() {
        TransactionFailureExplanation.from("Transaction reverted").shouldBeNull()
        TransactionFailureExplanation.from("SwapKit failed: reverted").shouldBeNull()
        TransactionFailureExplanation.from("Unknown chain: fantom").shouldBeNull()
    }

    @Test
    fun `an unrelated revert stays unexplained`() {
        TransactionFailureExplanation.from("ERC20: transfer amount exceeds balance").shouldBeNull()
        TransactionFailureExplanation.from("ERC20InsufficientAllowance").shouldBeNull()
    }

    @Test
    fun `null and blank reasons stay unexplained`() {
        TransactionFailureExplanation.from(null).shouldBeNull()
        TransactionFailureExplanation.from("").shouldBeNull()
        TransactionFailureExplanation.from("   ").shouldBeNull()
    }
}
