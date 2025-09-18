package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.v2.AccountItem
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
internal fun AccountList(
    onAccountClick: (AccountUiModel) -> Unit,
    snackbarState: VSSnackbarState,
    accounts: List<AccountUiModel>,
    isBalanceVisible: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    LazyColumn {
        itemsIndexed(
            items = accounts,
            key = { _, account -> account.chainName },
        ) { index, account ->
            Column {
                AccountItem(
                    modifier = Modifier.Companion.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    account = account,
                    isBalanceVisible = isBalanceVisible,
                    onClick = {
                        onAccountClick(account)
                    },
                    onCopy = {
                        coroutineScope.launch {
                            snackbarState.show("${account.chainName} Address Copied")
                        }
                    },
                )

                if (index != accounts.lastIndex) {

                    UiHorizontalDivider(
                        color = Theme.colors.borders.light,
                    )
                }
            }

        }

    }
}