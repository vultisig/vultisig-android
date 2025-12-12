package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
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
        onNextClick = model::next,
    )
}

@Composable
private fun VaultBackupOnboardingScreen(
    state: VaultBackupOnboardingUiModel,
    onNextClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            if (state.pageTotal != 1) {
                VsTopAppProgressBar(
                    title = stringResource(R.string.onboarding_security_backup_title),
                    progress = state.pageIndex + 1,
                    total = state.pageTotal,
                )
            }
        },
    ) { paddingValues ->

        val textItems = buildOnboardingPagesText(state.vaultType, state.vaultShares, state.action)

        OnboardingContent(
            state = OnboardingUiModel(
                pageIndex = state.pageIndex,
                pageTotal = state.pageTotal
            ),
            paddingValues = paddingValues,
            riveAnimation = when (state.vaultType) {
                Route.VaultInfo.VaultType.Fast -> R.raw.riv_fastvault_overview
                Route.VaultInfo.VaultType.Secure -> when {
                    state.vaultShares < 2 -> R.raw.riv_securevault_overview
                    state.vaultShares == 3 -> {
                        when (state.deviceIndex) {
                            1 -> R.raw.riv_2of3_securevault_overview_device2
                            2 -> R.raw.riv_2of3_securevault_overview_device3
                            else -> R.raw.riv_2of3_securevault_overview
                        }
                    }

                    state.vaultShares == 4 -> {
                        when (state.deviceIndex) {
                            1 -> R.raw.riv_3of4_securevault_overview_device2
                            2 -> R.raw.riv_3of4_securevault_overview_device3
                            3 -> R.raw.riv_3of4_securevault_overview_device3
                            else -> R.raw.riv_3of4_securevault_overview
                        }
                    }

                    else -> {
                        R.raw.riv_5plus_securevault_overview
                    }
                }
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
    vaultShares: Int = 2,
    action: TssAction,
) = when (vaultType) {
    Route.VaultInfo.VaultType.Secure -> listOf(
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_1_part_1,
                formatArgs = vaultShares,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_1_part_2,
                coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
            ),
        ),
        listOf(
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_2_part_1,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_2_part_2,
                coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_2_part_3,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.onboarding_security_backup_desc_page_2_part_4,
                coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
            ),
        )
    )

    Route.VaultInfo.VaultType.Fast ->
        when (action) {
            TssAction.Migrate -> {
                listOf(
                    listOf(
                        PartiallyGradientTextItem(
                            resId = R.string.onboarding_fast_backup_migration_desc_page_1_part_1,
                            coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                        ),
                        PartiallyGradientTextItem(
                            resId = R.string.onboarding_fast_backup_migration_desc_page_1_part_2,
                            coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                        ),
                    ),
                )
            }

            else -> listOf(
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_1_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_1_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                ),
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_2_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_2_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                ),
                listOf(
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_3_part_1,
                        coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                    ),
                    PartiallyGradientTextItem(
                        resId = R.string.onboarding_fast_backup_desc_page_3_part_2,
                        coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                    ),
                )
            )
        }
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

@Preview
@Composable
private fun VaultBackupOnboardingScreenPreview() {
    VaultBackupOnboardingScreen(
        state = VaultBackupOnboardingUiModel(
            vaultType = Route.VaultInfo.VaultType.Secure,
            vaultShares = 2,
            deviceIndex = 0,
            pageIndex = 0,
            pageTotal = 2,
            action = TssAction.KEYGEN,
        ),
        onNextClick = {}
    )
}