@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.screens.v2.components.VsButton
import com.vultisig.wallet.ui.theme.Theme

private enum class NotificationsIntroStep {
    Welcome,
    VaultOptIn,
}

internal data class VaultIntroItem(
    val vaultId: String,
    val vaultName: String,
    val isEnabled: Boolean,
)

@Composable
internal fun NotificationsIntroBottomSheet(
    vaults: List<VaultIntroItem>,
    onEnableAll: () -> Unit,
    onEnableVault: (String, Boolean) -> Unit,
    onNotNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var step by remember { mutableStateOf(NotificationsIntroStep.Welcome) }

    V2BottomSheet(onDismissRequest = onDismissRequest) {
        AnimatedContent(targetState = step, label = "NotificationsIntroStep") { currentStep ->
            when (currentStep) {
                NotificationsIntroStep.Welcome -> {
                    WelcomeStep(
                        onEnable = {
                            if (vaults.size <= 1) {
                                // For single vault, enable immediately and dismiss
                                onEnableAll()
                            } else {
                                step = NotificationsIntroStep.VaultOptIn
                            }
                        },
                        onNotNow = onNotNow,
                    )
                }

                NotificationsIntroStep.VaultOptIn -> {
                    VaultOptInStep(
                        vaults = vaults,
                        onEnableAll = onEnableAll,
                        onEnableVault = onEnableVault,
                        onDone = onDismissRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onEnable: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(size = 16.dp)

        Text(
            text = stringResource(R.string.notifications_are_here),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 12.dp)

        Text(
            text = stringResource(R.string.notifications_description),
            style = Theme.brockmann.body.s.regular,
            color = Theme.v2.colors.text.secondary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 24.dp)

        VsButton(
            label = stringResource(R.string.enable_push_notifications),
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.not_now),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.secondary,
            modifier = Modifier.clickOnce(onClick = onNotNow),
        )

        UiSpacer(size = 16.dp)
    }
}

@Composable
private fun VaultOptInStep(
    vaults: List<VaultIntroItem>,
    onEnableAll: () -> Unit,
    onEnableVault: (String, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(
            text = stringResource(R.string.choose_vaults_for_notifications),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 16.dp)

        // Enable All row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.enable_all),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier.weight(1f),
            )
            VsSwitch(
                checked = vaults.all { it.isEnabled },
                onCheckedChange = { if (it) onEnableAll() },
            )
        }

        UiSpacer(size = 8.dp)

        // Per-vault rows
        vaults.forEach { vault ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = vault.vaultName,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                    modifier = Modifier.weight(1f),
                )
                VsSwitch(
                    checked = vault.isEnabled,
                    onCheckedChange = { enabled -> onEnableVault(vault.vaultId, enabled) },
                )
            }
        }

        UiSpacer(size = 24.dp)

        VsButton(
            label = stringResource(android.R.string.ok),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(size = 16.dp)
    }
}
