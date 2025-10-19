package com.vultisig.wallet.ui.screens.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.usecases.VaultAndBalance
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.VaultCeilUiModel
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.components.v2.bottomsheets.navhost.VsBottomSheetNavController
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.models.folder.FolderUiModel
import com.vultisig.wallet.ui.models.folder.FolderViewModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.home.bottomsheets.vaultlist.components.VaultInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderScreen(
    folderId: String,
    vaultId: VaultId,
    navController: VsBottomSheetNavController,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(folderId) {
        viewModel.init(folderId)
    }


    FolderScreen(
        vaultId = vaultId,
        state = state,
        onMove = viewModel::onMoveVaults,
        onSelectVault = viewModel::selectVault,
        onBackClick = navController::popBackStack,
        onEditClick = {
            navController.navigate(
                Route.CreateFolder(
                    folderId = folderId
                )
            )
        }
    )
}

@Composable
internal fun FolderScreen(
    vaultId: VaultId,
    state: FolderUiModel,
    onMove: (Int, Int) -> Unit,
    onSelectVault: (VaultId) -> Unit,
    onBackClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .padding(
                all = 16.dp,
            )
    ) {

        UiSpacer(
            size = 12.dp
        )

        Row {
            VsCircleButton(
                icon = R.drawable.ic_caret_left,
                onClick = onBackClick,
                type = VsCircleButtonType.Secondary,
                size = VsCircleButtonSize.Small,
                hasBorder = true,
            )

            UiSpacer(
                size = 16.dp
            )

            VaultInfo(
                vaultName = state.folder?.name?: stringResource(R.string.folder),
                vaultCounts = state.vaults.size,
                totalBalance = state.totalBalance,
            )

            UiSpacer(
                weight = 1f
            )

            VsCircleButton(
                icon = R.drawable.pen_v2,
                onClick = onEditClick,
                type = VsCircleButtonType.Secondary,
                size = VsCircleButtonSize.Small,
                designType = DesignType.Shined,
                hasBorder = true,
            )

        }

        UiSpacer(
            size = 20.dp
        )


        VerticalReorderList(
            onMove = onMove,
            data = state.vaults,
            isReorderEnabled = false,
            key = { it.vault.id },
        ) { vaultAndBalance ->
            val (vault, balance) = vaultAndBalance

            VaultCeil(
                model = VaultCeilUiModel(
                    id = vault.id,
                    name = vault.name,
                    isFolder = false,
                    isFastVault = vault.isFastVault(),
                    vaultPart = vault.getVaultPart(),
                    signersSize = vault.signers.size,
                    balance = balance,
                ),
                isInEditMode = false,
                onSelect = onSelectVault,
                isSelected = vault.id == vaultId,
                activeVaultName = null,
                vaultCounts = null,
            )
        }

        UiSpacer(
            size = 12.dp
        )
    }

}

@Preview
@Composable
fun FolderScreenPreview() {
    FolderScreen(
        vaultId = "2",
        state = FolderUiModel(
            vaults = listOf(
                VaultAndBalance(
                    vault = generateFakeVault(),
                    balance = "$2",
                    balanceFiatValue = null,
                ),
                VaultAndBalance(
                    vault = generateFakeVault(),
                    balance = "$2",
                    balanceFiatValue = null,
                )
            )
        ),
        onMove = { _, _ -> },
        onSelectVault = {},
    )
}
