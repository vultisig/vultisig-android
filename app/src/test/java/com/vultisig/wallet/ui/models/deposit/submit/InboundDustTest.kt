package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class InboundDustTest {

    @Test
    fun `THORChain credits a 6-decimal amount equal to the threshold`() {
        requireXrp(amount = ONE_XRP, isThresholdDropped = false)
    }

    @Test
    fun `THORChain refuses a 6-decimal amount below the threshold, naming the threshold`() {
        val minimum = refusedMinimum(amount = ONE_XRP - BigInteger.ONE, isThresholdDropped = false)

        assertEquals("1", minimum)
    }

    @Test
    fun `a threshold that drops its own amount names one the coin can carry and that clears it`() {
        val minimum = refusedMinimum(amount = ONE_XRP, isThresholdDropped = true)

        assertEquals("1.000001", minimum)
        requireXrp(amount = BigInteger("1000001"), isThresholdDropped = true)
    }

    private fun refusedMinimum(amount: BigInteger, isThresholdDropped: Boolean): Any {
        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    requireXrp(amount, isThresholdDropped)
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.send_form_minimum_send_amount_is_requires_this, text.resId)
        return text.formatArgs.first()
    }

    private fun requireXrp(amount: BigInteger, isThresholdDropped: Boolean) =
        requireAboveInboundDust(
            amount = amount,
            decimals = 6,
            ticker = "XRP",
            inbound =
                THORChainInboundAddress(
                    chain = "XRP",
                    address = "rInbound",
                    halted = false,
                    chainLPActionsPaused = false,
                    chainTradingPaused = false,
                    dustThreshold = "100000000",
                ),
            chain = Chain.Ripple,
            isThresholdDropped = isThresholdDropped,
        )

    private companion object {
        val ONE_XRP: BigInteger = BigInteger("1000000")
    }
}
