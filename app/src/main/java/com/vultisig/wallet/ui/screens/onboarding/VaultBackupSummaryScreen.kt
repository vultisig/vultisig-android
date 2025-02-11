package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.onboarding.SummaryScreen
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.models.onboarding.VaultBackupSummaryViewModel
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType

@Composable
internal fun VaultBackupSummaryScreen(
    model: VaultBackupSummaryViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    BlockBackClick()

    SummaryScreen(
        checkState = state.isConsentChecked,
        animationRes = when (state.vaultType) {
            VaultType.Secure -> R.raw.riv_securevault_summary
            VaultType.Fast -> R.raw.riv_fastvault_summary
        },
        buttonText = R.string.vault_backup_summary_start_using_vault,
        onCheckChange = model::toggleCheck,
        onButtonClicked = model::next,
    )
}