package com.vultisig.wallet.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.onboarding.OnboardingContent
import com.vultisig.wallet.ui.components.topbar.VsTopAppProgressBar
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.models.onboarding.OnboardingSecurityVaultBackupViewModel
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingPage
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun OnboardingSecurityVaultBackupScreen(
    viewModel: OnboardingSecurityVaultBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler { }
    OnboardingSecurityVaultBackupScreen(state, viewModel::back, viewModel::next)
}
@Composable
private fun OnboardingSecurityVaultBackupScreen(
    state: OnboardingUiModel,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppProgressBar(
                title = stringResource(R.string.onboarding_security_backup_title),
                progress = state.currentPage.index + 1,
                total = state.pageTotal,
            )
        },
    ) { paddingValues ->

        OnboardingContent(
            state = state,
            paddingValues = paddingValues,
            riveAnimation = R.raw.securevault_overview,
            nextClick = onNextClick,
            textDescription = { page ->
                Description(page = page)
            },
        )
    }
}
@Composable
private fun Description(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    SequenceOfGradientText(
        listTextItems = when (page.index) {
            0 -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_1_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_1_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                )
            }

            else -> {
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_2_part_1,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_2_part_2,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_2_part_3,
                        coloring = GradientColoring.VsColor(Theme.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_security_backup_desc_page_2_part_4,
                        coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
                    ),
                )
            }
        },
        style = Theme.brockmann.headings.title1,
        modifier = modifier
    )
}