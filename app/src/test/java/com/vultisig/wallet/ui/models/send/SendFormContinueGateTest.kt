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
    ): SendFormUiModel =
        SendFormUiModel(
            dstAddressError = dstAddressError,
            defiType = defiType,
            memoError = memoError,
            hasMemo = hasMemo,
            isGasFeeLoading = false,
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
    fun `locked unbond node address never blocks continue`() {
        val state = model(dstAddressError = invalidRecipient, defiType = DeFiNavActions.UNBOND)

        assertFalse(state.isDstAddressEditable)
        assertFalse(state.isDstAddressBlocking)
        assertFalse(state.isContinueDisabled())
    }

    @Test
    fun `bond keeps its editable address gated`() {
        val state = model(dstAddressError = invalidRecipient, defiType = DeFiNavActions.BOND)

        assertTrue(state.isDstAddressEditable)
        assertTrue(state.isContinueDisabled())
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

        // Cosmos Hub raises max_memo_characters to 512, Noble keeps the SDK default of 256.
        assertNull(memoLengthErrorOrNull(Chain.GaiaChain, memo))
        assertNotNull(memoLengthErrorOrNull(Chain.Noble, memo))
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
        // 200 emoji: 200 characters, but 800 UTF-8 bytes — the length the node measures, and the
        // one reported so the error doesn't name a number that looks under the limit.
        val error = memoLengthErrorOrNull(Chain.Noble, "😀".repeat(200)) as UiText.FormattedText

        assertEquals(listOf<Any>(800, 256), error.formatArgs)
    }
}
