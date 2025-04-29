package com.vultisig.wallet.ui.screens.function

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun SwitchFunctionScreen(
    selectedToken: TokenMergeInfo,
    coinList: List<TokenMergeInfo>,
    onSelectCoin: (TokenMergeInfo) -> Unit,

    dstAddress: TextFieldState,
    onDstAddressLostFocus: () -> Unit,
    dstAddressError: UiText?,
    onSetDstAddress: (String) -> Unit,

    thorAddress: TextFieldState,
    onThorAddressLostFocus: () -> Unit,
    thorAddressError: UiText?,
    onSetThorAddress: (String) -> Unit,

    balance: UiText,
    amountFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit,
    amountError: UiText?,
) {
    FormSelection(
        selected = selectedToken,
        options = coinList,
        mapTypeToString = { it.ticker },
        onSelectOption = onSelectCoin,
    )

    FormTextFieldCard(
        title = stringResource(R.string.function_transfer_ibc_dst_address_input_title),
        hint = stringResource(R.string.function_transfer_ibc_dst_address_input_title),
        keyboardType = KeyboardType.Text,
        textFieldState = dstAddress,
        onLostFocus = onDstAddressLostFocus,
        error = dstAddressError,
    ) {
        val clipboard = LocalClipboardManager.current

        UiIcon(
            drawableResId = R.drawable.ic_paste,
            size = 20.dp,
            onClick = {
                clipboard.getText()
                    ?.toString()
                    ?.let(onSetDstAddress)
            }
        )

        UiSpacer(size = 8.dp)
    }

    FormTextFieldCard(
        title = stringResource(R.string.function_switch_thorchain_address),
        hint = stringResource(R.string.function_switch_thorchain_address),
        keyboardType = KeyboardType.Text,
        textFieldState = thorAddress,
        onLostFocus = onThorAddressLostFocus,
        error = thorAddressError,
    ) {
        val clipboard = LocalClipboardManager.current

        /*UiIcon(
            drawableResId = R.drawable.ic_paste,
            size = 20.dp,
            onClick = {
                clipboard.getText()
                    ?.toString()
                    ?.let(onSetThorAddress)
            }
        )*/

        UiSpacer(size = 8.dp)
    }

    FormTextFieldCard(
        title = stringResource(
            R.string.deposit_form_amount_title,
            balance.asString()
        ),
        hint = stringResource(R.string.send_amount_currency_hint),
        keyboardType = KeyboardType.Number,
        textFieldState = amountFieldState,
        onLostFocus = onAmountLostFocus,
        error = amountError,
    )
}
