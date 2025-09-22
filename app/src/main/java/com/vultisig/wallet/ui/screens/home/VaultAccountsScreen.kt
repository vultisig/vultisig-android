package com.vultisig.wallet.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.BackupWarning
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.ChainAccountItem
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultActionButton
import com.vultisig.wallet.ui.components.banners.UpgradeBanner
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.screens.scan.ScanQrBottomSheet
import com.vultisig.wallet.ui.screens.v2.home.HomePage
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
internal fun VaultAccountsScreen(
    vaultId: String,
    navHostController: NavHostController,
    viewModel: VaultAccountsViewModel = hiltViewModel(),
    isRearrangeMode: Boolean,
    onToggleVaultListClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(vaultId) {
        viewModel.loadData(vaultId)
    }

    DisposableEffect(key1 = vaultId) {
        onDispose {
            viewModel.closeLoadAccountJob()
        }
    }

    if (state.showMonthlyBackupReminder) {
        MonthlyBackupReminder(
            onDismiss = viewModel::dismissBackupReminder,
            onBackup = viewModel::backupVault,
            onDoNotRemind = viewModel::doNotRemindBackup,
        )
    }
    if (state.showCameraBottomSheet) {
        ScanQrBottomSheet (
            onDismiss = viewModel::dismissCameraBottomSheet,
            onScanSuccess = viewModel::onScanSuccess,
        )
    }

    HomePage(
        state = state,
        onRefresh = viewModel::refreshData,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        openCamera = viewModel::openCamera,
        onAccountClick = viewModel::openAccount,
        onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
        onOpenSettingsClick = viewModel::openSettings,
        onToggleVaultListClick = onToggleVaultListClick,
        onChooseChains = viewModel::openAddChainAccount,
    )

}


@Composable
private fun VaultAccountsScreen(
    state: VaultAccountsUiModel,
    modifier: Modifier = Modifier,
    isRearrangeMode: Boolean,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onRefresh: () -> Unit = {},
    openCamera: () -> Unit = {},
    onAccountClick: (AccountUiModel) -> Unit = {},
    onChooseChains: () -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onBackupWarningClick: () -> Unit = {},
    onMigrateClick: () -> Unit = {},
) {
    val snackBarHostState = remember {
        SnackbarHostState()
    }
    val coroutineScope = rememberCoroutineScope()

    BoxWithSwipeRefresh(
        onSwipe = onRefresh,
        isRefreshing = state.isRefreshing,
        modifier = Modifier.fillMaxSize()
    ) {

        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    if (state.showBackupWarning) {
                        BackupWarning(onBackupWarningClick)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom = 16.dp
                            )
                    ) {
                        if (state.showMigration) {
                            UpgradeBanner(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onMigrateClick)
                                    .testTag(VaultAccountsScreenTags.MIGRATE)
                            )
                        }

                        AnimatedContent(
                            targetState = state.totalFiatValue,
                            label = "ChainAccount FiatAmount",
                        ) { totalFiatValue ->
                            if (totalFiatValue != null) {
                                ToggleVisibilityText(
                                    text = totalFiatValue,
                                    isVisible = state.isBalanceValueVisible,
                                    onChangeVisibilityButtonClick = onToggleBalanceVisibility,
                                    style = Theme.menlo.heading4
                                        .copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 32.sp,
                                        ),
                                    color = Theme.colors.neutral100,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                UiPlaceholderLoader(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(32.dp),
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            VaultActionButton(
                                text = stringResource(R.string.chain_account_view_send),
                                color = Theme.colors.turquoise600Main,
                                modifier = Modifier.weight(1f),
                                onClick = onSend,
                            )
                            if (state.isSwapEnabled) {
                                VaultActionButton(
                                    text = stringResource(R.string.chain_account_view_swap),
                                    color = Theme.colors.persianBlue200,
                                    modifier = Modifier.weight(1f),
                                    onClick = onSwap,
                                )
                            }
                        }
                    }
                }

                items(
                    items = state.accounts,
                    key = { it.chainName },
                ) { account ->
                    ChainAccountItem(
                        account = account,
                        isBalanceVisible = state.isBalanceValueVisible,
                        onClick = {
                            onAccountClick(account)
                        },
                        isRearrangeMode = isRearrangeMode,
                        onCopy = {
                            coroutineScope.launch {
                                snackBarHostState.showSnackbar(it)
                            }
                        }
                    )

                }

                item {
                    UiSpacer(
                        size = 16.dp,
                    )
                    UiPlusButton(
                        title = stringResource(R.string.vault_choose_chains),
                        onClick = onChooseChains,
                        modifier = Modifier
                            .testTag("VaultAccountsScreen.chooseChains")
                    )
                    UiSpacer(
                        size = 64.dp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 40.dp,
                    contentDescription = "join keysign",
                    tint = Theme.colors.oxfordBlue600Main,
                    onClick = openCamera,
                    modifier = Modifier
                        .background(
                            color = Theme.colors.turquoise600Main,
                            shape = CircleShape,
                        )
                        .padding(all = 10.dp)
                )
            }

            SnackbarHost(snackBarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Preview
@Composable
private fun VaultAccountsScreenPreview() {
    VaultAccountsScreen(
        isRearrangeMode = false,
        state = VaultAccountsUiModel(
            vaultName = "Vault Name",
            totalFiatValue = "$1000",
            accounts = listOf(
                AccountUiModel(
                    chainName = "Ethereum",
                    logo = R.drawable.ethereum,
                    address = "0x1234567890",
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$1000",
                    assetsSize = 4,
                    model = Address(
                        chain = Chain.Ethereum,
                        address = "0x123456",
                        accounts = emptyList()
                    )
                ),
                AccountUiModel(
                    chainName = "Bitcoin",
                    logo = R.drawable.bitcoin,
                    address = "123456789abcdef",
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$1000",
                    model = Address(
                        chain = Chain.Bitcoin,
                        address = "0x123456",
                        accounts = emptyList()
                    )
                ),
            ),
        ),
    )
}

internal object VaultAccountsScreenTags {
    const val MIGRATE = "VaultAccountsScreen.migrate"
}