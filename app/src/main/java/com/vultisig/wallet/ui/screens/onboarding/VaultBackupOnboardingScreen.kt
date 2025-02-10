package com.vultisig.wallet.ui.screens.onboarding

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
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingUiModel
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingViewModel
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingUiModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultBackupOnboardingScreen(
    model: VaultBackupOnboardingViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    BlockBackClick()

    VaultBackupOnboardingScreen(
        state = state,
        onBackClick = model::back,
        onNextClick = model::next,
    )
}

@Composable
private fun VaultBackupOnboardingScreen(
    state: VaultBackupOnboardingUiModel,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppProgressBar(
                title = stringResource(R.string.onboarding_security_backup_title),
                progress = state.pageIndex + 1,
                total = state.pageTotal,
            )
        },
    ) { paddingValues ->

        val textItems = buildOnboardingPagesText(state.vaultType)

        OnboardingContent(
            state = OnboardingUiModel(
                currentPage = state.currentPage,
                pageIndex = state.pageIndex,
                pageTotal = state.pageTotal
            ),
            paddingValues = paddingValues,
            riveAnimation = when (state.vaultType) {
                Route.VaultInfo.VaultType.Fast -> R.raw.riv_fastvault_overview
                Route.VaultInfo.VaultType.Secure -> R.raw.riv_securevault_overview
            },
            nextClick = onNextClick,
            textDescription = { index ->
                Description(
                    textItems = textItems[index]
                )
            },
        )
    }
}

@Composable
private fun buildOnboardingPagesText(
    vaultType: Route.VaultInfo.VaultType,
) = when (vaultType) {
    Route.VaultInfo.VaultType.Secure -> listOf(
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_1_part_1,
                coloring = GradientColoring.VsColor(Theme.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_1_part_2,
                coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
            ),
        ),
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
    )

    Route.VaultInfo.VaultType.Fast -> listOf(
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_1_part_1,
                coloring = GradientColoring.VsColor(Theme.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_1_part_2,
                coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
            ),
        ),
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_2_part_1,
                coloring = GradientColoring.VsColor(Theme.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_2_part_2,
                coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
            ),
        ),
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_3_part_1,
                coloring = GradientColoring.VsColor(Theme.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_fast_backup_desc_page_3_part_2,
                coloring = GradientColoring.Gradient(Theme.colors.gradients.primary),
            ),
        )
    )
}


@Composable
private fun Description(
    textItems: List<PartiallyGradientTextItem>,
    modifier: Modifier = Modifier,
) {
    SequenceOfGradientText(
        listTextItems = textItems,
        style = Theme.brockmann.headings.title1,
        modifier = modifier
    )
}