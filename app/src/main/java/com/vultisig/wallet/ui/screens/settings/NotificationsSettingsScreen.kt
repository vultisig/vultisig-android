package com.vultisig.wallet.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.components.v2.icons.VaultIcon
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.NotificationsSettingsUiState
import com.vultisig.wallet.ui.models.settings.NotificationsSettingsViewModel
import com.vultisig.wallet.ui.models.settings.VaultNotificationUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NotificationsSettingsScreen() {
    val viewModel = hiltViewModel<NotificationsSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    LaunchedEffect(Unit) {
        viewModel.requestNotificationPermission.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Permission not required below Android 13
                viewModel.onNotificationPermissionResult(granted = true)
            }
        }
    }

    NotificationsSettingsScreen(
        state = state,
        onMasterToggle = viewModel::onMasterToggle,
        onVaultToggle = viewModel::onVaultToggle,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun NotificationsSettingsScreen(
    state: NotificationsSettingsUiState,
    onMasterToggle: (Boolean) -> Unit,
    onVaultToggle: (String, Boolean) -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(R.string.notifications), onBackClick = onBackClick) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            MasterNotificationToggle(isChecked = state.masterEnabled, onToggle = onMasterToggle)

            if (state.masterEnabled && state.vaults.isNotEmpty()) {
                UiSpacer(size = 22.dp)
                Text(
                    text = stringResource(R.string.vault_notifications),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
                UiSpacer(size = 12.dp)
                Column(
                    modifier =
                        Modifier.background(
                            color = Theme.v2.colors.backgrounds.surface1,
                            shape = RoundedCornerShape(size = 12.dp),
                        )
                ) {
                    state.vaults.forEachIndexed { index, vault ->
                        VaultNotificationToggle(
                            vault = vault,
                            onToggle = { enabled -> onVaultToggle(vault.vaultId, enabled) },
                            isLastItem = index == state.vaults.lastIndex,
                        )
                    }
                }
            }

            UiSpacer(size = 24.dp)
        }
    }
}

@Composable
private fun MasterNotificationToggle(isChecked: Boolean, onToggle: (Boolean) -> Unit) {

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.push_notifications),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 4.dp)

            Text(
                text =
                    stringResource(
                        R.string
                            .get_notified_when_your_signature_is_required_or_a_device_requests_access
                    ),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.primary,
            )
        }
        UiSpacer(size = 70.dp)
        VsSwitch(checked = isChecked, onCheckedChange = onToggle)
    }
}

@Composable
private fun VaultNotificationToggle(
    vault: VaultNotificationUiModel,
    onToggle: (Boolean) -> Unit,
    isLastItem: Boolean,
) {
    Column() {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VaultIcon(
                isFastVault = vault.isFastVault,
                size = 20.dp,
                contentDescription = null,
                modifier =
                    Modifier.border(
                            width = 1.dp,
                            color = Theme.v2.colors.variables.bordersLight,
                            shape = RoundedCornerShape(size = 99.dp),
                        )
                        .background(
                            color = Theme.v2.colors.variables.backgroundsSurface12,
                            shape = RoundedCornerShape(size = 99.dp),
                        )
                        .padding(12.dp),
            )
            Text(
                text = vault.vaultName,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier.weight(1f),
            )
            VsSwitch(checked = vault.isEnabled, onCheckedChange = onToggle)
        }
        if (!isLastItem) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsSettingsScreenPreview() {
    NotificationsSettingsScreen(
        state =
            NotificationsSettingsUiState(
                masterEnabled = true,
                vaults =
                    listOf(
                        VaultNotificationUiModel(
                            vaultId = "1",
                            vaultName = "Secure Vault",
                            isEnabled = true,
                            isFastVault = false,
                        ),
                        VaultNotificationUiModel(
                            vaultId = "2",
                            vaultName = "Trading Vault",
                            isEnabled = false,
                            isFastVault = true,
                        ),
                        VaultNotificationUiModel(
                            vaultId = "3",
                            vaultName = "Main Vault",
                            isEnabled = true,
                            isFastVault = false,
                        ),
                    ),
            ),
        onMasterToggle = {},
        onVaultToggle = { _, _ -> },
        onBackClick = {},
    )
}
