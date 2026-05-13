package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AccountList(
    onAccountClick: (AccountUiModel) -> Unit,
    snackbarState: VSSnackbarState,
    accounts: List<AccountUiModel>,
    isBalanceVisible: Boolean,
    showAddress: Boolean = true,
) {
    Column {
        accounts.forEachIndexed { index, account ->
            key(account.chainName) {
                val addressCopiedMessage =
                    stringResource(R.string.address_copied, account.chainName)
                Column {
                    AccountItem(
                        modifier = Modifier.Companion.padding(horizontal = 16.dp, vertical = 12.dp),
                        account = account,
                        isBalanceVisible = isBalanceVisible,
                        showAddress = showAddress,
                        onClick = { onAccountClick(account) },
                        onCopy = { snackbarState.show(addressCopiedMessage) },
                    )

                    if (index != accounts.lastIndex) {
                        UiHorizontalDivider(color = Theme.v2.colors.border.light)
                    }
                }
            }
        }
    }
}
