package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.onboarding.SummaryScreen
import com.vultisig.wallet.ui.models.onboarding.OnboardingSummaryViewModel

@Composable
internal fun OnboardingSummaryScreen(
    model: OnboardingSummaryViewModel = hiltViewModel(),
) {
    val checkState by model.checkState.collectAsState()

    SummaryScreen(
        checkState = checkState,
        animationRes = R.raw.quick_summary,
        buttonText = R.string.onboarding_summary_button,
        onCheckChange = model::toggleCheck,
        onButtonClicked = model::createVault,
    )
}