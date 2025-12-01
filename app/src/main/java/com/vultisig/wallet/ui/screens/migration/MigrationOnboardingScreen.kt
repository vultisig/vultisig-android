package com.vultisig.wallet.ui.screens.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.util.GradientColoring
import com.vultisig.wallet.ui.components.util.PartiallyGradientTextItem
import com.vultisig.wallet.ui.components.util.SequenceOfGradientText
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun MigrationOnboardingScreen(
    model: MigrationOnboardingViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    var currentPage by remember { mutableIntStateOf(0) }
    val pages = getPages(state.vaultType)

    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.migration_onboarding_upgrade_your_vault),
                onBackClick = {
                    if (currentPage <= 0) {
                        model.back()
                    } else {
                        currentPage--
                    }
                },
            )
        },
        content = { contentPadding ->
            val page = pages[currentPage]
            MigrationOnboardingContent(
                onNext = {
                    if (currentPage >= pages.size - 1) {
                        model.upgrade()
                    } else {
                        currentPage++
                    }
                },
                animation = page.animation,
                text = page.text,
                buttonText = page.buttonText,
                modifier = Modifier
                    .padding(contentPadding),
            )
        }
    )
}

private data class MigrationOnboardingPage(
    val animation: Int,
    val text: List<PartiallyGradientTextItem>,
    val buttonText: String,
)

@Composable
private fun getPages(
    vaultType: Route.VaultInfo.VaultType,
) = listOfNotNull(
    MigrationOnboardingPage(
        animation = R.raw.riv_upgrade_animation,
        text = listOf(
            PartiallyGradientTextItem(
                resId = R.string.migration_onboarding_step_1_text_start,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.migration_onboarding_step_1_text_emphasized,
                coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary)
            ),
            PartiallyGradientTextItem(
                resId = R.string.migration_onboarding_step_1_text_end,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
        ),
        buttonText = stringResource(R.string.migration_onboarding_upgrade_now),
    ),
    if (vaultType == Route.VaultInfo.VaultType.Secure) {
        MigrationOnboardingPage(
            animation = R.raw.riv_choose_vault,
            text = listOf(
                PartiallyGradientTextItem(
                    resId = R.string.migration_onboarding_step_3_text_start,
                    coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                ),
                PartiallyGradientTextItem(
                    resId = R.string.migration_onboarding_step_3_text_emphasize,
                    coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
                ),
                PartiallyGradientTextItem(
                    resId = R.string.migration_onboarding_step_3_text_end,
                    coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
                ),
            ),
            buttonText = stringResource(R.string.peer_discovery_action_next_title)
        )
    } else null,
    MigrationOnboardingPage(
        animation = R.raw.riv_choose_vault,
        text = listOf(
            PartiallyGradientTextItem(
                resId = R.string.migration_onboarding_step_2_text_start,
                coloring = GradientColoring.Gradient(Theme.v2.colors.gradients.primary),
            ),
            PartiallyGradientTextItem(
                resId = R.string.migration_onboarding_step_2_text_end,
                coloring = GradientColoring.VsColor(Theme.v2.colors.text.primary),
            ),
        ),
        buttonText = stringResource(R.string.peer_discovery_action_next_title)
    ),
)

@Composable
internal fun MigrationOnboardingContent(
    animation: Int,
    text: List<PartiallyGradientTextItem>,
    buttonText: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        RiveAnimation(
            animation = animation,
            modifier = Modifier.fillMaxWidth(),
            onInit = {
                // TODO it's not great that it doesn't update with passing new animation into params
                it.setRiveResource(
                    animation
                )
            }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier
                .padding(
                    vertical = 56.dp,
                    horizontal = 36.dp,
                )
                .align(Alignment.BottomCenter)
        ) {
            SequenceOfGradientText(
                listTextItems = text,
                style = Theme.brockmann.headings.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            VsButton(
                label = buttonText,
                onClick = onNext,
                size = VsButtonSize.Medium,
                modifier = Modifier
                    .testTag(MigrationOnboardingScreenTags.NEXT)
            )
        }
    }
}

@Preview
@Composable
private fun MigrationOnboardingScreenPreview() {
    MigrationOnboardingScreen()
}

object MigrationOnboardingScreenTags {
    const val NEXT = "MigrationOnboardingScreen.next"
}