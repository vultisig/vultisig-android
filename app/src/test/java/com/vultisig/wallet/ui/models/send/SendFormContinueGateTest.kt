package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class SendFormContinueGateTest {

    private val invalidRecipient =
        UiText.StringResource(R.string.send_error_invalid_recipient_address)

    private fun model(
        dstAddressError: UiText? = null,
        defiType: DeFiNavActions? = null,
    ): SendFormUiModel =
        SendFormUiModel(
            dstAddressError = dstAddressError,
            defiType = defiType,
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
}
