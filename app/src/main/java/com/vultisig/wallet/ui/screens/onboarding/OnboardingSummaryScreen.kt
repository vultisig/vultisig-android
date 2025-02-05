package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.models.onboarding.OnboardingSummaryViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun OnboardingSummaryScreen( //TODO: Add rive animation and checkbox
    viewModel: OnboardingSummaryViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .fillMaxSize(),
    ) {
        Column (Modifier.align(Alignment.BottomCenter)) {
            VsButton(
                onClick = viewModel::createVault,
                label = stringResource(id = R.string.onboarding_summary_button),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            UiSpacer(32.dp)
        }
    }
}