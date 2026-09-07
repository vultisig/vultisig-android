package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.TransactionFailureExplanation
import com.vultisig.wallet.ui.models.TransactionHistoryItemUiModel
import com.vultisig.wallet.ui.models.TransactionStatusUiModel
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/**
 * A settled swap row has to name both assets without being opened — the destination is the half the
 * card exists to report, and it was the half a collapsed row never showed.
 */
@HiltAndroidTest
class SwapTransactionCardPairTest {

    // The card needs nothing injected, but the test application's Hilt component does have to
    // exist: anything the system starts against this process mid-run asks for it on creation.
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aSettledSwapNamesBothLegsAndTheRouteItTook() {
        start(swap)

        compose.onNodeWithText("+0.0261 BTC").assertIsDisplayed()
        compose.onNodeWithText("-125.5 RUNE").assertIsDisplayed()
        compose.onNodeWithText("RUNE → BTC").assertIsDisplayed()
    }

    @Test
    fun aSettledSwapLeavesTheRouteToThePillAloneAndDropsTheProviderBadge() {
        start(swap)

        compose
            .onNodeWithText(
                "${context.getString(R.string.transaction_history_via_prefix)} THORChain"
            )
            .assertDoesNotExist()
    }

    @Test
    fun aFailedSwapStillNamesBothLegsAndKeepsItsReasonBesideTheStatus() {
        start(
            swap.copy(
                status =
                    TransactionStatusUiModel.Failed(
                        reason = UiText.DynamicString("Insufficient output"),
                        explanation = TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE,
                    )
            )
        )

        compose.onNodeWithText("+0.0261 BTC").assertIsDisplayed()
        compose.onNodeWithText("-125.5 RUNE").assertIsDisplayed()
        compose.onNodeWithText("RUNE → BTC").assertIsDisplayed()
        compose
            .onNodeWithText(
                context.getString(TransactionFailureExplanation.MIN_OUTPUT_SLIPPAGE.labelRes)
            )
            .assertIsDisplayed()
    }

    @Test
    fun aRefundedSwapIsStillPairedAndSaysSo() {
        start(
            swap.copy(
                status =
                    TransactionStatusUiModel.Refunded(
                        reason = UiText.DynamicString("pool is halted")
                    )
            )
        )

        // A refund is settled, not in progress, so the row owes both assets like any other closed
        // card — the user needs to know which leg came back.
        compose.onNodeWithText("+0.0261 BTC").assertIsDisplayed()
        compose.onNodeWithText("-125.5 RUNE").assertIsDisplayed()
        compose.onNodeWithText("RUNE → BTC").assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.transaction_status_refunded_label))
            .assertIsDisplayed()
    }

    @Test
    fun aLimitOrderRowIsPairedTheSameWayOnceItHasSettled() {
        start(swap.copy(isLimitOrder = true))

        compose.onNodeWithText("+0.0261 BTC").assertIsDisplayed()
        compose.onNodeWithText("RUNE → BTC").assertIsDisplayed()
    }

    @Test
    fun aLongRouteCannotSqueezeTheLegsBelowItsOwnHalf() {
        start(
            swap.copy(
                fromToken = "USDC.eth.axl",
                toAmount = "12,345,678.90123456",
                toToken = "ASTRO-IBC",
            )
        )

        // The pill used to be measured at its full intrinsic width before the legs were offered
        // anything, so a long route left the amounts as little more than an ellipsis. Both halves
        // of the row now get the same share of what the pair logo leaves.
        val pill = compose.onNodeWithText("USDC.eth.axl → ASTRO-IBC").getUnclippedBoundsInRoot()
        // Equal weights can still land a pixel apart on an odd row width, so allow a hair.
        compose
            .onNodeWithText("+12,345,678.90123456 ASTRO-IBC")
            .assertWidthIsAtLeast(pill.width - 1.dp)
    }

    @Test
    fun anInProgressSwapKeepsItsStackedPayoutLayoutAndItsProviderBadge() {
        start(swap.copy(status = TransactionStatusUiModel.Broadcasted))

        // The two-legged pill belongs to the settled card; an in-progress card already spells the
        // route out down the column, and still owes the user the provider it is waiting on.
        compose.onNodeWithText("RUNE → BTC").assertDoesNotExist()
        compose
            .onNodeWithText(context.getString(R.string.transaction_history_expected_payout_label))
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "${context.getString(R.string.transaction_history_via_prefix)} THORChain"
            )
            .assertIsDisplayed()
    }

    private fun start(item: TransactionHistoryItemUiModel.Swap) {
        compose.setContent { OnBoardingComposeTheme { SwapTransactionCard(item = item) } }
    }

    private val swap =
        TransactionHistoryItemUiModel.Swap(
            id = "1",
            txHash = "0xabc123",
            chain = "THORChain",
            status = TransactionStatusUiModel.Confirmed,
            explorerUrl = "",
            timestamp = 0L,
            fromToken = "RUNE",
            fromAmount = "125.5",
            fromChain = "THORChain",
            fromTokenLogo = R.drawable.rune,
            toToken = "BTC",
            toAmount = "0.0261",
            toChain = "Bitcoin",
            toTokenLogo = R.drawable.bitcoin,
            provider = "THORChain",
            providerLogo = R.drawable.rune,
            fiatValue = "$1,204.00",
            fromAddress = null,
            toAddress = null,
            feeEstimate = null,
        )
}
