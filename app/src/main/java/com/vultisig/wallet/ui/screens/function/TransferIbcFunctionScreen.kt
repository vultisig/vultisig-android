package com.vultisig.wallet.ui.screens.function

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.utils.UiText

@Composable
internal fun TransferIbcFunctionScreen(
    selectedChain: Chain,
    chainList: List<Chain>,
    onSelectChain: (Chain) -> Unit,

    selectedToken: TokenMergeInfo,
    coinList: List<TokenMergeInfo>,
    onSelectCoin: (TokenMergeInfo) -> Unit,

    dstAddress: TextFieldState,
    onDstAddressLostFocus: () -> Unit,
    dstAddressError: UiText?,
    onSetDstAddress: (String) -> Unit,

    balance: UiText,
    amountFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit,
    amountError: UiText?,

    memoFieldState: TextFieldState,
    onMemoLostFocus: () -> Unit,
    memoError: UiText?,
) {
    FormSelection(
        selected = selectedChain,
        options = chainList,
        mapTypeToString = { "${it.raw} ${it.feeUnit.uppercase()}" },
        onSelectOption = onSelectChain,
    )

    FormSelection(
        selected = selectedToken,
        options = coinList,
        mapTypeToString = { it.ticker.uppercase() },
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
        title = stringResource(R.string.deposit_form_amount_title_without_balance),
        hint = stringResource(R.string.send_amount_currency_hint),
        keyboardType = KeyboardType.Number,
        textFieldState = amountFieldState,
        onLostFocus = onAmountLostFocus,
        error = amountError,
    )

    FormTextFieldCard(
        title = stringResource(R.string.deposit_form_custom_memo_optional_title),
        hint = stringResource(R.string.deposit_form_custom_memo_optional_title),
        keyboardType = KeyboardType.Text,
        textFieldState = memoFieldState,
        onLostFocus = onMemoLostFocus,
        error = memoError,
    )
}