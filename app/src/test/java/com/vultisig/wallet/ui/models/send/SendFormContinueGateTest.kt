package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class SendFormContinueGateTest {

    private val invalidRecipient =
        UiText.StringResource(R.string.send_error_invalid_recipient_address)

    private fun model(
        dstAddressError: UiText? = null,
        defiType: DeFiNavActions? = null,
        memoError: UiText? = null,
        hasMemo: Boolean = false,
        isGasFeeLoading: Boolean = false,
        hasAmountInput: Boolean = false,
        isAmountSelectionLoading: Boolean = false,
        isLoading: Boolean = false,
    ): SendFormUiModel =
        SendFormUiModel(
            dstAddressError = dstAddressError,
            defiType = defiType,
            memoError = memoError,
            hasMemo = hasMemo,
            isGasFeeLoading = isGasFeeLoading,
            hasAmountInput = hasAmountInput,
            isAmountSelectionLoading = isAmountSelectionLoading,
            isLoading = isLoading,
        )

    @Test
    fun `invalid recipient blocks continue on a plain send`() {
        val state = model(dstAddressError = invalidRecipient)

        assertTrue(state.isDstAddressBlocking)
        assertTrue(state.isContinueDisabled())
    }

    @Test
    fun `valid recipient does not block continue`() {
        val state = model()

        assertFalse(state.isDstAddressBlocking)
        assertFalse(state.isContinueDisabled())
    }

    @Test
    fun `percentage selection blocks continue and shows it as loading`() {
        // The calculation is about to overwrite the amount field, so Continue must not submit the
        // amount the user just replaced — and it says so with the spinner instead of greying out.
        val state = model(isAmountSelectionLoading = true)

        assertTrue(state.isContinueDisabled())
        assertTrue(state.isContinueLoading())
    }

    @Test
    fun `fee recompute after an amount is entered shows continue as loading`() {
        val state = model(isGasFeeLoading = true, hasAmountInput = true)

        assertTrue(state.isContinueDisabled())
        assertTrue(state.isContinueLoading())
    }

    @Test
    fun `no amount entered leaves continue plainly disabled`() {
        // isGasFeeLoading starts true before any amount exists, but no estimate is armed there —
        // a spinner would claim work that isn't running.
        val state = model(isGasFeeLoading = true)

        assertTrue(state.isContinueDisabled())
        assertFalse(state.isContinueLoading())
    }

    @Test
    fun `submit in flight shows continue as loading`() {
        val state = model(isLoading = true)

        assertTrue(state.isContinueDisabled())
        assertTrue(state.isContinueLoading())
    }

    @Test
    fun `input the user must fix disables continue without a spinner`() {
        val state = model(dstAddressError = invalidRecipient)

        assertTrue(state.isContinueDisabled())
        assertFalse(state.isContinueLoading())
    }

    @Test
    fun `a settled form shows no loading`() {
        val state = model(hasAmountInput = true)

        assertFalse(state.isContinueDisabled())
        assertFalse(state.isContinueLoading())
    }

    @Test
    fun `over-long memo blocks continue on a plain send`() {
        val error = memoLengthErrorOrNull(Chain.Noble, "a".repeat(257))
        assertNotNull(error)

        val state = model(memoError = error, hasMemo = true)

        assertTrue(state.isMemoBlocking)
        assertTrue(state.isContinueDisabled())
    }

    @Test
    fun `memo error on a token without a memo field never blocks continue`() {
        val state = model(memoError = memoLengthErrorOrNull(Chain.Noble, "a".repeat(257)))

        assertFalse(state.isMemoBlocking)
        assertFalse(state.isContinueDisabled())
    }

    @Test
    fun `memo at the chain limit does not error`() {
        assertNull(memoLengthErrorOrNull(Chain.Noble, "a".repeat(256)))
    }

    @Test
    fun `limit is resolved per chain`() {
        val memo = "a".repeat(300)

        // Cosmos Hub and Terra 2.0 raise max_memo_characters to 512, Noble keeps the SDK default
        // of 256.
        assertNull(memoLengthErrorOrNull(Chain.GaiaChain, memo))
        assertNull(memoLengthErrorOrNull(Chain.Terra, memo))
        assertNotNull(memoLengthErrorOrNull(Chain.Noble, memo))
    }

    @Test
    fun `thorchain allows a memo up to the envelope limit a plain send is checked against`() {
        // 250 bytes is thornode's MsgDeposit-only cap; a plain send carries the memo in the tx
        // envelope, checked against max_memo_characters instead.
        assertNull(memoLengthErrorOrNull(Chain.ThorChain, "a".repeat(256)))
        assertNotNull(memoLengthErrorOrNull(Chain.ThorChain, "a".repeat(257)))
    }

    @Test
    fun `a whitespace-only memo never blocks continue`() {
        // The submit path drops a blank memo to null, so the signed transaction carries no memo at
        // all — its length can't be a reason to disable Continue.
        assertNull(memoLengthErrorOrNull(Chain.Noble, " ".repeat(300)))
    }

    @Test
    fun `chains without a published memo ceiling never error`() {
        assertNull(memoLengthErrorOrNull(Chain.Ethereum, "a".repeat(5_000)))
        assertNull(memoLengthErrorOrNull(null, "a".repeat(5_000)))
    }

    @Test
    fun `error names the current length and the limit`() {
        val error = memoLengthErrorOrNull(Chain.Noble, "a".repeat(26_342)) as UiText.FormattedText

        assertEquals(R.string.send_error_memo_too_long, error.resId)
        assertEquals(listOf<Any>(26_342, 256), error.formatArgs)
    }

    @Test
    fun `memo within the character limit but over the node's byte limit errors`() {
        // 200 emoji: 200 characters, but 800 UTF-8 bytes — the length the node measures against the
        // limit, and the one reported so the error doesn't name a number that looks like it fits.
        val error = memoLengthErrorOrNull(Chain.Noble, "😀".repeat(200)) as UiText.FormattedText

        assertEquals(listOf<Any>(800, 256), error.formatArgs)
    }
}
