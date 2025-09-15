package com.vultisig.wallet.ui.screens.v2.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.AccountItem
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.AnimatedPrice
import com.vultisig.wallet.ui.screens.v2.home.components.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.ChooseVaultButton
import com.vultisig.wallet.ui.screens.v2.home.components.SearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.TopRow
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButtonType
import com.vultisig.wallet.ui.screens.v2.home.components.UpgradeBanner
import com.vultisig.wallet.ui.screens.v2.home.components.WalletEarnSelect
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePage(
    state: VaultAccountsUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onRefresh: () -> Unit = {},
    openCamera: () -> Unit = {},
    onToggleVaultListClick: () -> Unit = {},
    onAccountClick: (AccountUiModel) -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onMigrateClick: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onChooseChains: () -> Unit = {},
) {

    val snackbarState = rememberVsSnackbarState()
    val coroutineScope = rememberCoroutineScope()

    ScaffoldWithExpandableTopBar(
        snackbarState = snackbarState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        topBarCollapsedContent = {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.colors.backgrounds.primary)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChooseVaultButton(
                        vaultName = state.vaultName,
                        isFastVault = false,
                        onClick = onToggleVaultListClick,
                    )

                    Column(
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = stringResource(R.string.home_portfolio_balance),
                            color = Theme.colors.text.extraLight,
                            style = Theme.brockmann.body.s.medium
                        )
                        UiSpacer(
                            size = 2.dp
                        )

                        AnimatedPrice(
                            totalFiatValue = state.totalFiatValue,
                            isVisible = state.isBalanceValueVisible,
                            style = Theme.satoshi.price.bodyS,
                            color = Theme.colors.text.primary,
                        )
                    }
                }

                UiSpacer(
                    size = 16.dp
                )

                UiHorizontalDivider(
                    color = Theme.colors.borders.light
                )

                UiSpacer(
                    size = 8.dp
                )
            }
        },
        topBarExpandedContent = {
            val context = LocalContext.current
            val displayMetrics = context.resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Theme.colors.primary.accent1,
                                Theme.colors.backgrounds.primary
                            ),
                            radius = screenWidthPx,
                            center = androidx.compose.ui.geometry.Offset(
                                screenWidthPx / 2,
                                -screenWidthPx * 0.5f
                            )
                        )
                    )
                    .padding(all = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopRow(
                    onOpenSettingsClick = onOpenSettingsClick,
                    onToggleVaultListClick = onToggleVaultListClick,
                    vaultName = state.vaultName,
                    isFastVault = state.isFastVault,
                )
                UiSpacer(
                    40.dp
                )
                BalanceBanner(
                    isVisible = state.isBalanceValueVisible,
                    balance = state.totalFiatValue,
                    onToggleVisibility = onToggleBalanceVisibility
                )

                UiSpacer(32.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        20.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    TransactionTypeButtonType.entries.forEach {
                        TransactionTypeButton(
                            txType = it,
                            isSelected = false,
                            onClick = {
                                when (it) {
                                    TransactionTypeButtonType.SEND -> onSend()
                                    TransactionTypeButtonType.SWAP -> onSwap()
                                }
                            }
                        )
                    }
                }

                UiSpacer(
                    size = 16.dp
                )

            }
        },
        bottomBarContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WalletEarnSelect()
                    CameraButton(
                        onClick = openCamera
                    )
                }
            }
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .background(Theme.colors.backgrounds.primary)
                        .fillMaxSize()
                ) {
                    if (state.showMigration) {
                        UiSpacer(12.dp)
                        UpgradeBanner(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            onUpgradeClick = onMigrateClick,
                        )
                        UiSpacer(20.dp)
                        UiHorizontalDivider(
                            color = Theme.colors.borders.light,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        UiSpacer(16.dp)
                    }

                    SearchBar(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                        ),
                        onEditClick = onChooseChains,
                    )

                    TopShineContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        LazyColumn {
                            itemsIndexed(
                                items = state.accounts,
                                key = { _, account -> account.chainName },
                            ) { index, account ->
                                Column {
                                    AccountItem(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp
                                        ),
                                        account = account,
                                        isBalanceVisible = state.isBalanceValueVisible,
                                        onClick = {
                                            onAccountClick(account)
                                        },
                                        onCopy = {
                                            coroutineScope.launch {
                                                snackbarState.show("${account.chainName} Address Copied")
                                            }
                                        },
                                    )

                                    if (index != state.accounts.lastIndex) {

                                        UiHorizontalDivider(
                                            color = Theme.colors.borders.light,
                                        )
                                    }
                                }

                            }

                        }
                    }
                }
                BottomFadeEffect()
            }
        }
    )
}

@Composable
private fun BoxScope.BottomFadeEffect() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Theme.colors.backgrounds.primary
                    )
                )
            )
            .align(Alignment.BottomCenter),
    )
}


@Preview
@Composable
private fun PreviewHomePage() {
    HomePage(
        state = VaultAccountsUiModel()
    )
}

