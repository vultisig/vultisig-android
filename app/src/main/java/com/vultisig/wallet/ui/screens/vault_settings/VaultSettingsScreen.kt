package com.vultisig.wallet.ui.screens.vault_settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.backup.BackupMethodBottomSheet
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.screens.settings.SettingsBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VaultSettingsScreen() {
    val viewModel = hiltViewModel<VaultSettingsViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()

    BackHandler(onBack = viewModel::onBackClick)

    VaultSettingsScreen(
        uiModel = uiModel,
        onSettingsClick = viewModel::onSettingsItemClick,
        onBackClick = viewModel::onBackClick,
        onLocalBackupClick = viewModel::onLocalBackupClick,
        onServerBackupClick = viewModel::onServerBackupClick,
        onDismissBackupVaultBottomSheet = viewModel::onDismissBackupVaultBottomSheet,
        onDismissBiometricsBottomSheet = viewModel::onDismissBiometricFastSignBottomSheet,
        biometricTextFieldState = viewModel.passwordTextFieldState,
        onSaveBiometricsClick = viewModel::onSaveEnableBiometricFastSign,
        onToggleVisibilityClick = viewModel::togglePasswordVisibility,
    )
}

@Composable
private fun VaultSettingsScreen(
    uiModel: VaultSettingsState,
    onSaveBiometricsClick: () -> Unit = {},
    onDismissBiometricsBottomSheet: () -> Unit = {},
    biometricTextFieldState: TextFieldState,
    onSettingsClick: (VaultSettingsItem) -> Unit,
    onBackClick: () -> Unit = {},
    onLocalBackupClick: () -> Unit = {},
    onServerBackupClick: () -> Unit = {},
    onDismissBackupVaultBottomSheet: () -> Unit = {},
    onToggleVisibilityClick: () -> Unit = {},
) {

    val settingGroups = uiModel.settingGroups

    V2Scaffold(
        title =
            if (uiModel.isAdvanceSetting) stringResource(R.string.vault_settings_advanced_title)
            else stringResource(R.string.vault_settings_title),
        onBackClick = onBackClick,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            settingGroups.filter(VaultSettingsGroupUiModel::isVisible).forEach { group ->
                SettingsBox(title = group.title?.asString()) {
                    val enabledSettings = group.items.filter(VaultSettingsItem::enabled)
                    enabledSettings.forEachIndexed { index, item ->
                        SettingItem(
                            item = item.value,
                            onClick = { onSettingsClick(item) },
                            isLastItem = index == enabledSettings.lastIndex,
                            tint =
                                if (item is VaultSettingsItem.Delete) Theme.v2.colors.alerts.error
                                else null,
                        )
                    }
                }
                UiSpacer(14.dp)
            }

            if (uiModel.isBackupVaultBottomSheetVisible) {
                BackupMethodBottomSheet(
                    onDismissRequest = onDismissBackupVaultBottomSheet,
                    onDeviceBackupClick = onLocalBackupClick,
                    onServerBackupClick = onServerBackupClick,
                )
            }

            if (uiModel.isBiometricFastSignBottomSheetVisible) {
                BiometricFastSignBottomSheet(
                    onDismissBiometricsBottomSheet,
                    biometricTextFieldState,
                    onToggleVisibilityClick,
                    onSaveBiometricsClick,
                    uiModel.biometricsEnableUiModel,
                )
            }
        }
    }
}

@Composable
private fun BiometricFastSignBottomSheet(
    onDismissBiometricsBottomSheet: () -> Unit,
    biometricTextFieldState: TextFieldState,
    onToggleVisibilityClick: () -> Unit,
    onSaveBiometricsClick: () -> Unit,
    biometricsEnableUiModel: BiometricsEnableUiModel,
) {
    VsModalBottomSheet(onDismissRequest = onDismissBiometricsBottomSheet) {
        BiometricFastSignBottomSheetContent(
            uiModel = biometricsEnableUiModel,
            passwordTextFieldState = biometricTextFieldState,
            onToggleVisibilityClick = onToggleVisibilityClick,
            onSaveClick = onSaveBiometricsClick,
        )
    }
}

@Composable
private fun BiometricFastSignBottomSheetContent(
    uiModel: BiometricsEnableUiModel,
    passwordTextFieldState: TextFieldState,
    onToggleVisibilityClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vault_password_biometric_enable_biometrics_fast_signing),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.v2.colors.text.primary,
        )

        FadingHorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        val errorMessage = uiModel.passwordErrorMessage?.asString()
        VsTextInputField(
            textFieldState = passwordTextFieldState,
            type =
                VsTextInputFieldType.Password(
                    isVisible = uiModel.isPasswordVisible,
                    onVisibilityClick = onToggleVisibilityClick,
                ),
            hint =
                uiModel.passwordHint?.asString()
                    ?: stringResource(R.string.import_file_screen_hint_password),
            footNote = errorMessage,
            innerState =
                if (errorMessage != null) VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default,
        )

        UiSpacer(14.dp)

        VsButton(
            state =
                when {
                    uiModel.isLoading -> VsButtonState.Disabled
                    uiModel.isSaveEnabled -> VsButtonState.Enabled
                    else -> VsButtonState.Disabled
                },
            onClick = onSaveClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiModel.isLoading) {
                VsCircularLoading(modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = stringResource(R.string.add_vault_save),
                    style = Theme.brockmann.button.semibold.medium,
                    color = Theme.v2.colors.text.button.primary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun BiometricFastSignBottomSheetContentPreview() {
    BiometricFastSignBottomSheetContent(
        uiModel = BiometricsEnableUiModel(),
        passwordTextFieldState = rememberTextFieldState(),
        onToggleVisibilityClick = {},
        onSaveClick = {},
    )
}
