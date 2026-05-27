package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the ERC-20 approve-spender derivation — the middle link between [SwapKitQuoteSource] setting
 * `tx.allowanceTarget` and `ERC20ApprovePayload.spender`. The two ends are covered elsewhere
 * (SwapKitQuoteSourceTest / KeysignShareViewModelSwapApprovalTest); without this, a regression
 * collapsing the derivation to `tx.to` would keep every test green while routing the approve to the
 * wrong spender (a SwapKit swap then reverts with ERC20InsufficientAllowance).
 */
internal class ApproveSpenderTest {

    private fun tx(to: String, allowanceTarget: String?) =
        OneInchSwapTxJson(
            from = "0xfrom",
            to = to,
            allowanceTarget = allowanceTarget,
            gas = 21000,
            data = "0x",
            value = "0",
            gasPrice = "1",
        )

    @Test
    fun `uses the token-transfer proxy when allowanceTarget is present (SwapKit)`() {
        val spender =
            approveSpenderFor(
                tx(
                    to = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743", // swap entry contract
                    allowanceTarget = "0x6c0ad82f9721a6dc986381d19338601a2e6370e5", // proxy
                )
            )

        assertEquals("0x6c0ad82f9721a6dc986381d19338601a2e6370e5", spender)
    }

    @Test
    fun `falls back to tx_to when allowanceTarget is null (1inch Kyber LiFi)`() {
        val spender = approveSpenderFor(tx(to = "0xrouter", allowanceTarget = null))

        assertEquals("0xrouter", spender)
    }
}
