package com.vultisig.wallet.ui.screens.folder

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.SelectionItem
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultCeil
import com.vultisig.wallet.ui.components.VaultCeilUiModel
import com.vultisig.wallet.ui.components.VaultSwitch
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.models.folder.FolderUiModel
import com.vultisig.wallet.ui.models.folder.FolderViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderScreen(
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val nameFieldState = viewModel.nameFieldState

    Scaffold(
        bottomBar = {
            if (state.isEditMode) {
                Box(Modifier.imePadding()) {
                    MultiColorButton(
                        backgroundColor = Theme.colors.turquoise600Main,
                        textColor = Theme.colors.oxfordBlue600Main,
                        iconColor = Theme.colors.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        disabled = state.nameError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                        text = stringResource(id = R.string.save_changes),
                        onClick = viewModel::edit,
                    )
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.folder?.name ?: "",
                        style = Theme.montserrat.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = Theme.colors.neutral0,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.oxfordBlue800,
                    titleContentColor = Theme.colors.neutral0,
                ),
                navigationIcon = {
                    IconButton(onClick = clickOnce(viewModel::back)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_caret_left),
                            contentDescription = "",
                        )
                    }
                },
                actions = {
                    val modifier = remember { Modifier.padding(horizontal = 16.dp) }
                    if (state.isEditMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.trash_outline),
                            contentDescription = "edit",
                            tint = Theme.colors.miamiMarmalade,
                            modifier = modifier
                                .clickOnce(onClick = viewModel::deleteFolder)
                                .size(24.dp)
                        )
                    }
                    else {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_edit_square_24),
                            contentDescription = "edit",
                            tint = Theme.colors.neutral0,
                            modifier = modifier.clickOnce(onClick = viewModel::edit)
                        )
                    }
                }
            )
        },
    ) { contentPadding ->
        val errorText = state.error
        if (errorText != null) {
            UiAlertDialog(
                title = stringResource(R.string.dialog_default_error_title),
                text = errorText.asString(),
                confirmTitle = stringResource(R.string.try_again),
                onDismiss = viewModel::hideError,
            )
        }

        VerticalReorderList(
            onMove = viewModel::onMoveVaults,
            data = state.vaults,
            isReorderEnabled = state.isEditMode,
            key = { it.id },
            contentPadding = PaddingValues(
                vertical = 16.dp,
                horizontal = 16.dp,
            ),
            modifier = Modifier
                .padding(contentPadding),
            beforeContents =
            if (state.isEditMode)
                listOf<@Composable LazyItemScope.() -> Unit> @Composable {
                    TopFolderScreenContent(state, nameFieldState)
                }
            else
                null,
            afterContents =
            if (state.isEditMode && state.availableVaults.isNotEmpty())
                listOf<@Composable LazyItemScope.() -> Unit> @Composable {
                    BottomFolderScreenContent (state, viewModel::tryToCheckVault)
                }
            else
                null,
        ) { vault ->
            val check = remember { mutableStateOf(true) }
            val trailingContent = @Composable {
                VaultSwitch(
                    modifier = Modifier.height(20.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Theme.colors.neutral0,
                        checkedBorderColor = Theme.colors.turquoise800,
                        checkedTrackColor = Theme.colors.turquoise800,
                        uncheckedThumbColor = Theme.colors.neutral0,
                        uncheckedBorderColor = Theme.colors.oxfordBlue400,
                        uncheckedTrackColor = Theme.colors.oxfordBlue400
                    ),
                    checked = check.value,
                    onCheckedChange = {
                        check.value = viewModel.tryToCheckVault(it, vault.id)
                    },
                )
            }

            VaultCeil(
                model = VaultCeilUiModel(
                    id = vault.id,
                    name = vault.name,
                    isFolder = false,
                    isFastVault = vault.isFastVault(),
                    vaultPart = vault.getVaultPart(),
                    signersSize = vault.signers.size,
                ),
                isInEditMode = state.isEditMode,
                onSelect = viewModel::selectVault,
                trailingIcon = if (state.isEditMode) trailingContent else null,
            )
        }
    }
}

@Composable
private fun BottomFolderScreenContent(
    state: FolderUiModel,
    tryToCheckVault: (Boolean, String) -> Boolean,
) {
    UiSpacer(size = 20.dp)
    Text(
        text = stringResource(id = R.string.select_vaults_to_add_to_the_folder),
        color = Theme.colors.neutral200,
        style = Theme.montserrat.body1,
    )
    UiSpacer(size = 16.dp)

    state.availableVaults.forEach { vault ->
        val check = remember { mutableStateOf(false) }
        SelectionItem(
            title = vault.name,
            isChecked = check.value,
            onCheckedChange = {
                check.value = tryToCheckVault(it, vault.id)
            },
        )
        UiSpacer(size = 16.dp)
    }
}

@Composable
private fun TopFolderScreenContent(
    state: FolderUiModel,
    nameFieldState: TextFieldState
) {
    FormTextFieldCard(
        title = stringResource(id = R.string.create_folder_name),
        hint = state.folder?.name ?: "",
        error = state.nameError,
        keyboardType = KeyboardType.Text,
        textFieldState = nameFieldState,
    )
    UiSpacer(size = 16.dp)
    Text(
        text = stringResource(id = R.string.current_vaults),
        color = Theme.colors.neutral200,
        style = Theme.montserrat.body1,
    )
    UiSpacer(size = 16.dp)
}
