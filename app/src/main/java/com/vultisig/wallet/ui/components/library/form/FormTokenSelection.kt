package com.vultisig.wallet.ui.components.library.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.utils.tokenBalanceUiModels

@Composable
internal fun FormTokenSelection(
    selectedToken: TokenBalanceUiModel?,
    onSelectToken: () -> Unit,
) {
    FormTokenCard(
        selectedTitle = selectedToken?.title ?: "",
        availableToken = selectedToken?.balance ?: "",
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