package com.vultisig.wallet.ui.screens.vault_settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldStyle
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.referral.ReferralCodeBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.screens.settings.SettingItemLayout
import com.vultisig.wallet.ui.screens.settings.SettingsBox
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.settings.bottomsheets.FeatureGateBottomSheet
import com.vultisig.wallet.ui.screens.settings.bottomsheets.FeatureGateSheetStyle
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.ShareSheetGrabber
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
        onDismissCustomRpcUpsell = viewModel::onDismissCustomRpcUpsell,
        onUnlockCustomRpcTier = viewModel::onUnlockCustomRpcTier,
        onContinueReferral = viewModel::onContinueReferralBottomSheet,
        onDismissReferral = viewModel::onDismissReferralBottomSheet,
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
    onDismissCustomRpcUpsell: () -> Unit = {},
    onUnlockCustomRpcTier: () -> Unit = {},
    onContinueReferral: () -> Unit = {},
    onDismissReferral: () -> Unit = {},
) {

    val settingGroups = uiModel.settingGroups

    V2Scaffold(
        title =
            if (uiModel.isAdvanceSetting) stringResource(R.string.vault_settings_advanced_title)
            else stringResource(R.string.vault_settings_title),
        onBackClick = onBackClick,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val titleStyle =
                if (uiModel.isAdvanceSetting) Theme.brockmann.headings.subtitle
                else Theme.brockmann.supplementary.footnote
            settingGroups.filter(VaultSettingsGroupUiModel::isVisible).forEach { group ->
                SettingsBox(title = group.title?.asString(), layout = SettingItemLayout.List) {
                    val enabledSettings = group.items.filter(VaultSettingsItem::enabled)
                    enabledSettings.forEachIndexed { index, item ->
                        SettingItem(
                            item = item.value,
                            onClick = { onSettingsClick(item) },
                            isLastItem = index == enabledSettings.lastIndex,
                            tint =
                                if (item is VaultSettingsItem.Delete) Theme.v2.colors.alerts.error
                                else null,
                            titleStyle = titleStyle,
                            layout = SettingItemLayout.List,
                        )
                    }
                }
                UiSpacer(14.dp)
            }

            if (uiModel.hasToShowReferralCodeSheet) {
                ReferralCodeBottomSheet(
                    onContinue = onContinueReferral,
                    onDismissRequest = onDismissReferral,
                )
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

            if (uiModel.showCustomRpcUpsell) {
                FeatureGateBottomSheet(
                    featureTitle = stringResource(R.string.custom_rpc_title),
                    featureDescription = stringResource(R.string.custom_rpc_gate_description),
                    featureIcon = R.drawable.ic_custom_rpc_medallion,
                    style = FeatureGateSheetStyle.Settings,
                    requiredTier = TierType.SILVER,
                    balanceText = uiModel.customRpcVultBalance,
                    thresholdText = uiModel.customRpcVultThreshold,
                    isBelowThreshold = uiModel.customRpcIsBelowThreshold,
                    onGetVult = onUnlockCustomRpcTier,
                    onDismiss = onDismissCustomRpcUpsell,
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
    VsModalBottomSheet(
        onDismissRequest = onDismissBiometricsBottomSheet,
        dragHandle = { ShareSheetGrabber(top = 5.dp, bottom = 10.dp) },
        shape = RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS),
        scrimColor = Theme.v2.colors.neutrals.n900.copy(alpha = 0.5f),
    ) {
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
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.vault_settings_biometric_fast),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.neutrals.n50,
        )

        UiSpacer(16.dp)

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
                    ?: stringResource(R.string.fast_vault_password_screen_password_hint),
            footNote = errorMessage,
            innerState =
                if (errorMessage != null) VsTextInputFieldInnerState.Error
                else VsTextInputFieldInnerState.Default,
            invisibleIcon = R.drawable.eye_closed,
            style = VsTextInputFieldStyle.SettingsOutlined,
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

private val SHEET_CORNER_RADIUS = 38.dp

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

@Preview
@Composable
internal fun VaultSettingsScreenPreview() {
    VaultSettingsScreen(
        uiModel = VaultSettingsState(settingGroups = previewVaultSettingGroups(isAdvanced = false)),
        biometricTextFieldState = rememberTextFieldState(),
        onSettingsClick = {},
    )
}

@Preview
@Composable
internal fun VaultAdvancedSettingsScreenPreview() {
    VaultSettingsScreen(
        uiModel =
            VaultSettingsState(
                settingGroups = previewVaultSettingGroups(isAdvanced = true),
                isAdvanceSetting = true,
            ),
        biometricTextFieldState = rememberTextFieldState(),
        onSettingsClick = {},
    )
}

/** Every row the design shows, with each row's visibility rule switched on. */
private fun previewVaultSettingGroups(isAdvanced: Boolean) =
    defaultVaultSettingGroups.map { group ->
        group.copy(
            isVisible = (group.id == "ADVANCED") == isAdvanced,
            items =
                group.items.map { item ->
                    when (item) {
                        is VaultSettingsItem.BiometricFastSign ->
                            item.copy(isEnabled = true, isBiometricEnabled = true)
                        is VaultSettingsItem.Security -> item.copy(isEnabled = true)
                        is VaultSettingsItem.LockTime -> item.copy(isEnabled = true)
                        is VaultSettingsItem.Migrate -> item.copy(isEnabled = true)
                        is VaultSettingsItem.Reshare -> item.copy(isEnabled = true)
                        is VaultSettingsItem.DilithiumKeygen -> item.copy(isEnabled = true)
                        is VaultSettingsItem.CustomRpc -> item.copy(isEnabled = true)
                        else -> item
                    }
                },
        )
    }
