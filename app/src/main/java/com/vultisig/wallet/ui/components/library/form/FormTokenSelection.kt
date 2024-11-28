package com.vultisig.wallet.ui.components.library.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.utils.tokenBalanceUiModels

@Composable
internal fun FormTokenSelection(
    selectedToken: TokenBalanceUiModel?,
    showBalance: Boolean = true,
    onSelectToken: () -> Unit,
) {
    val availableTokenText =
        if (showBalance)
            stringResource(R.string.form_token_selection_balance, selectedToken?.balance.toString())
        else ""

    FormTokenCard(
        selectedTitle = selectedToken?.title ?: "",
        availableToken = availableTokenText,
        selectedIcon = selectedToken?.tokenLogo
            ?: R.drawable.ethereum,
        chainLogo = selectedToken?.chainLogo,
        onClick = onSelectToken,
    )
}

@Preview
@Composable
private fun FormTokenSelectionPreview() {
    val m = tokenBalanceUiModels
    FormTokenSelection(
        selectedToken = m[0],
        onSelectToken = {}
    )
}