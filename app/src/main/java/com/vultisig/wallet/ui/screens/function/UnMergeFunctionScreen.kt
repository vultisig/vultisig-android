package com.vultisig.wallet.ui.screens.function

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.form.FormSelection
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun UnMergeFunctionScreen(
    selectedToken: TokenMergeInfo,
    coinList: List<TokenMergeInfo>,
    onSelectCoin: (TokenMergeInfo) -> Unit,
    shares: UiText,
    amountFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit,
    amountError: UiText?,
    onLoadRujiBalances: () -> Unit,
) {

    LaunchedEffect(Unit) { onLoadRujiBalances() }

    FormSelection(
        selected = selectedToken,
        options = coinList,
        onSelectOption = onSelectCoin,
        mapTypeToString = { it.denom.uppercase() },
    )

    FormTextFieldCard(
        title = stringResource(
            R.string.deposit_form_shares_title,
            shares.asString()
        ),
        hint = stringResource(R.string.send_shares_currency_hint),
        keyboardType = KeyboardType.Number,
        textFieldState = amountFieldState,
        onLostFocus = onAmountLostFocus,
        error = amountError,
    )
}