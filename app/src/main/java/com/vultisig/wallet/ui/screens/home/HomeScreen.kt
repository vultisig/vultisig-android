package com.vultisig.wallet.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.models.HomeUiModel
import com.vultisig.wallet.ui.models.HomeViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    HomeScreen(
        navController = navController,
        state = state,
        onOpenSettings = viewModel::openSettings,
        onEdit = viewModel::edit,
        onToggleVaults = viewModel::toggleVaults,
        onSelectVault = viewModel::selectVault,
        onSelectFolder = viewModel::selectFolder,
        onCreateNewVault = viewModel::addVault,
        onCreateNewFolder = viewModel::addFolder,
        onShareVaultQr = viewModel::shareVaultQr,
        isEditMode = viewModel.isEditMode
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    navController: NavHostController,
    state: HomeUiModel,
    onOpenSettings: () -> Unit = {},
    onEdit: () -> Unit = {},
    isEditMode: Boolean,
    onToggleVaults: () -> Unit = {},
    onSelectVault: (vaultId: String) -> Unit = {},
    onSelectFolder: (folderId: String) -> Unit = {},
    onCreateNewVault: () -> Unit = {},
    onCreateNewFolder: () -> Unit = {},
    onShareVaultQr: () -> Unit = {},
) {
    val caretRotation by animateFloatAsState(
        targetValue = if (state.showVaultList) -90f else 90f,
        animationSpec = tween(300),
        label = "HomeScreen caretRotation",
    )

    val screenTitle = if (state.showVaultList) {
        stringResource(id = R.string.home_screen_title)
    } else {
        state.vaultName
    }

    Box{
        if (state.selectedVaultId != null) {
            VaultAccountsScreen(
                navHostController = navController,
                vaultId = state.selectedVaultId,
                isRearrangeMode = state.isChainRearrangeMode,
                onToggleVaultListClick = onToggleVaults,
            )
        }

        AnimatedVisibility(
            visible = state.showVaultList,
            enter = slideInVertically(initialOffsetY = { height -> -height }),
            exit = slideOutVertically(targetOffsetY = { height -> -height })
        ) {
            VaultListScreen(
                onSelectVault = onSelectVault,
                onSelectFolder = onSelectFolder,
                onCreateNewVault = onCreateNewVault,
                onCreateNewFolder = onCreateNewFolder,
                isRearrangeMode = state.isVaultRearrangeMode,
            )
        }
    }

    /*Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clickable(onClick = onToggleVaults)
                            .testTag("HomeScreen.title"),
                    ) {
                        AnimatedContent(
                            targetState = screenTitle,
                            label = "HomeScreen title",
                        ) { screenTitle ->
                            Text(
                                text = screenTitle,
                                style = Theme.montserrat.subtitle1,
                                fontWeight = FontWeight.Bold,
                                color = Theme.colors.neutral0,
                            )
                        }

                        UiIcon(
                            drawableResId = R.drawable.ic_small_caret_right,
                            size = 12.dp,
                            modifier = Modifier.rotate(caretRotation)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.oxfordBlue800,
                    titleContentColor = Theme.colors.neutral0,
                ),
                navigationIcon = {
                    IconButton(onClick = clickOnce(onOpenSettings)) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "settings",
                            tint = Theme.colors.neutral0,
                        )
                    }
                },
                actions = {
                    val modifier = remember { Modifier.padding(horizontal = 16.dp) }
                    when {
                        state.showVaultList && isEditMode -> {
                            Text(
                                text = stringResource(id = R.string.home_scree_done),
                                style = Theme.menlo.subtitle1,
                                fontWeight = FontWeight.Bold,
                                color = Theme.colors.neutral0,
                                modifier = modifier.clickOnce(onClick = onEdit)
                            )
                        }
                        state.showVaultList && !isEditMode -> {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_edit_square_24),
                                contentDescription = "edit",
                                tint = Theme.colors.neutral0,
                                modifier = modifier.clickOnce(onClick = onEdit)
                            )
                        }
                    }
                }
            )
        },
    ) {
        Box(
            modifier = Modifier.padding(it),
        ) {
            if (state.selectedVaultId != null) {
                VaultAccountsScreen(
                    navHostController = navController,
                    vaultId = state.selectedVaultId,
                    isRearrangeMode = state.isChainRearrangeMode
                )
            }

            AnimatedVisibility(
                visible = state.showVaultList,
                enter = slideInVertically(initialOffsetY = { height -> -height }),
                exit = slideOutVertically(targetOffsetY = { height -> -height })
            ) {
                VaultListScreen(
                    onSelectVault = onSelectVault,
                    onSelectFolder = onSelectFolder,
                    onCreateNewVault = onCreateNewVault,
                    onCreateNewFolder = onCreateNewFolder,
                    isRearrangeMode = state.isVaultRearrangeMode,
                )
            }
        }
    }*/
}

@Preview
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        navController = rememberNavController(),
        isEditMode = false,
        state = HomeUiModel(
            showVaultList = false,
            vaultName = "Vault Name",
            selectedVaultId = "1",
        )
    )
}
