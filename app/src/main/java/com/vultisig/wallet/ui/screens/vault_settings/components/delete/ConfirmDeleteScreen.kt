package com.vultisig.wallet.ui.screens.vault_settings.components.delete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.form.VsUiCheckbox
import com.vultisig.wallet.ui.components.topbar.VsTopbar
import com.vultisig.wallet.ui.screens.SettingInfoHorizontalItem
import com.vultisig.wallet.ui.screens.itemModifier
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.Theme.colors


@Composable
internal fun ConfirmDeleteScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<ConfirmDeleteViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    ConfirmDeleteScreen(
        cautions = uiModel.cautionsBeforeDelete,
        checkedCautionIndexes = uiModel.checkedCautionIndexes,
        isDeleteButtonActive = uiModel.isDeleteButtonEnabled,
        vaultDeleteUiModel = uiModel.vaultDeleteUiModel,
        onBackClick = {
            navHostController.popBackStack()
        },
        onItemCheckChangeClick = viewModel::changeCheckCaution,
        onConfirmClick = viewModel::delete
    )
}

@Composable
private fun ConfirmDeleteScreen(
    cautions: List<Int>,
    checkedCautionIndexes: List<Int>,
    isDeleteButtonActive: Boolean,
    vaultDeleteUiModel: VaultDeleteUiModel,
    onItemCheckChangeClick: (Int, Boolean) -> Unit,
    onConfirmClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            VsTopbar(
                title = stringResource(R.string.vault_settings_delete_title),
                onBackClick = onBackClick,
            )
        },
        bottomBar = {
            VsButton(
                onClick = onConfirmClick,
                state = if (isDeleteButtonActive.not()) VsButtonState.Disabled else
                    VsButtonState.Enabled,
                label = stringResource(R.string.confirm_delete_delete_vault),
                variant = VsButtonVariant.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    ) {

        Column(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DeleteVaultBanner()
            UiSpacer(14.dp)
            SettingInfoHorizontalItem(
                key = stringResource(R.string.vault_settings_delete_vault_name),
                value = vaultDeleteUiModel.name,
            )
            UiSpacer(12.dp)
            SettingInfoHorizontalItem(
                key = stringResource(R.string.vault_settings_delete_vault_value),
                value = vaultDeleteUiModel.totalFiatValue,
            )
            UiSpacer(12.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .height(intrinsicSize = IntrinsicSize.Min)
            ) {
                VerticalVaultInfo(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_part),
                    value = stringResource(
                        id = R.string.vault_part_n_of_t,
                        vaultDeleteUiModel.vaultPart,
                        vaultDeleteUiModel.deviceList.size,
                    )
                )

                VerticalVaultInfo(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_id),
                    value = vaultDeleteUiModel.localPartyId
                )
            }

            UiSpacer(12.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .height(intrinsicSize = IntrinsicSize.Min)
            ) {
                VerticalVaultInfo2(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_ecdsa_key),
                    value = vaultDeleteUiModel.pubKeyECDSA,
                )

                VerticalVaultInfo2(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_eddsa_key),
                    value = vaultDeleteUiModel.pubKeyEDDSA
                )
            }


            UiSpacer(weight = 1f)
            UiSpacer(size = 16.dp)

            cautions.forEachIndexed { index, resId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checkedCautionIndexes.contains(index),
                            onValueChange = { checked ->
                                onItemCheckChangeClick(
                                    index,
                                    checked
                                )
                            },
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VsUiCheckbox(checked = checkedCautionIndexes.contains(index)) { checked ->
                        onItemCheckChangeClick(
                            index,
                            checked
                        )
                    }

                    Text(
                        text = stringResource(resId),
                        style = Theme.brockmann.supplementary.caption,
                        color = colors.text.light
                    )
                }
            }

            UiSpacer(size = 16.dp)
        }

    }
}


@Composable
private fun VerticalVaultInfo(
    modifier: Modifier = Modifier,
    key: String,
    value: String
) {
    Column(
        modifier = modifier.itemModifier(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.primary
        )
        Text(
            text = value,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.primary
        )
    }
}


@Composable
private fun VerticalVaultInfo2(
    modifier: Modifier = Modifier,
    key: String,
    value: String
) {
    Column(
        modifier = modifier.itemModifier(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.primary
        )
        Text(
            text = value,
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.extraLight
        )
    }
}

@Composable
private fun DeleteVaultBanner() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        UiSpacer(
            size = 24.dp
        )
        UiIcon(
            drawableResId = R.drawable.ic_warning,
            contentDescription = "warning",
            size = 24.dp,
            tint = colors.alerts.error
        )

        UiSpacer(
            size = 14.dp
        )

        Text(
            text = stringResource(R.string.vault_settings_delete_title),
            style = Theme.brockmann.headings.title2,
            color = colors.alerts.error
        )

        UiSpacer(
            size = 8.dp
        )

        Text(
            text = stringResource(R.string.confirm_delete_permanent_delete_message),
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.extraLight
        )
    }
}
@Preview
@Composable
private fun ConfirmDeleteScreenPreview() {
    ConfirmDeleteScreen(
        cautions = listOf(
            com.vultisig.wallet.R.string.confirm_delete_delete_vault,
        ),
        checkedCautionIndexes = listOf(0, 1),
        isDeleteButtonActive = false,
        vaultDeleteUiModel = VaultDeleteUiModel(
            name = "My Vault",
            totalFiatValue = "$12,345.67",
            vaultPart = 2,
            deviceList = listOf("Device 1", "Device 2", "Device 3"),
            localPartyId = "123e4567-e89b-12d3-a456-426614174000",
            pubKeyECDSA = "04bfcabf...9c3d1e5f",
            pubKeyEDDSA = "ed25519:3b6a27bc...",
        ),
        onItemCheckChangeClick = { _, _ -> },
        onConfirmClick = {},
        onBackClick = {}
    )
}



