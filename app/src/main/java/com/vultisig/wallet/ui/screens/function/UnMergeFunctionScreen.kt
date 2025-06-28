package com.vultisig.wallet.ui.screens.function

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun UnMergeFunctionScreen(
    balance: UiText,
    amountFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit,
    amountError: UiText?,
) {
    FormTextFieldCard(
        title = stringResource(
            R.string.deposit_form_amount_title_without_balance,
            balance.asString()
        ),
        hint = stringResource(R.string.send_amount_currency_hint),
        keyboardType = KeyboardType.Number,
        textFieldState = amountFieldState,
        onLostFocus = onAmountLostFocus,
        error = amountError,
    )
}