package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.util.BlockBackClick
import com.vultisig.wallet.ui.components.v2.texts.highlightedText
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingEvent
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingUiModel
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VaultBackupOnboardingScreen(model: VaultBackupOnboardingViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()

    BlockBackClick()

    VaultBackupOnboardingScreen(uiState = state, onEvent = model::onEvent)
}

@Composable
internal fun VaultBackupOnboardingScreen(
    uiState: VaultBackupOnboardingUiModel,
    onEvent: (VaultBackupOnboardingEvent) -> Unit,
) {
    V3Scaffold(
        applyDefaultPaddings = false,
        content = {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.size(width = 350.dp, height = 260.dp)) {
                    RiveAnimation(animation = uiState.rive, modifier = Modifier.fillMaxSize())
                }
                UiSpacer(size = 14.dp)

                Column(
                    modifier =
                        Modifier.weight(1f)
                            .padding(
                                horizontal = V3Scaffold.PADDING_HORIZONTAL,
                                vertical = V3Scaffold.PADDING_VERTICAL,
                            )
                            .verticalScroll(rememberScrollState())
                ) {
                    GradientTitleText(
                        gradientPart = stringResource(R.string.backup_backups),
                        regularPart =
                            " " + stringResource(R.string.backup_your_new_recovery_method),
                        style = Theme.brockmann.headings.title2,
                    )
                    UiSpacer(size = 16.dp)

                    Text(
                        text =
                            highlightedText(
                                mainText = stringResource(R.string.backup_backups_power_your_vault),
                                highlightedWords =
                                    listOf(stringResource(R.string.backup_own_keyword)),
                                mainTextStyle = Theme.brockmann.supplementary.footnote,
                                mainTextColor = Theme.v2.colors.text.tertiary,
                                highlightTextStyle =
                                    Theme.brockmann.supplementary.footnote.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                highlightTextColor = Theme.v2.colors.text.primary,
                            )
                    )
                    UiSpacer(size = 32.dp)

                    uiState.tips.forEachIndexed { index, (title, description, logo) ->
                        Row {
                            UiIcon(
                                drawableResId = logo,
                                contentDescription = null,
                                size = 24.dp,
                                tint = Theme.v2.colors.alerts.info,
                            )
                            UiSpacer(size = 16.dp)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = title.asString(),
                                    style = Theme.brockmann.headings.subtitle,
                                    color = Theme.v2.colors.neutrals.n50,
                                )
                                UiSpacer(size = 8.dp)

                                Text(
                                    text = description.asString(),
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.tertiary,
                                )
                            }
                        }
                        if (index < uiState.tips.lastIndex) {
                            UiSpacer(size = 16.dp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.vault_setup_i_understand),
                onClick = { onEvent(VaultBackupOnboardingEvent.Next) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun GradientTitleText(
    gradientPart: String,
    regularPart: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Theme.brockmann.headings.title1,
) {
    Text(
        modifier = modifier,
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(brush = Theme.v2.colors.gradients.primary)) {
                    append(gradientPart)
                }
                withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(regularPart) }
            },
        style = style,
    )
}

@Preview
@Composable
private fun VaultBackupOnboardingScreenPreview() {
    VaultBackupOnboardingScreen(
        uiState =
            VaultBackupOnboardingUiModel(
                tips = VaultBackupOnboardingViewModel.FastVaultBackupOnboardingTips,
                rive = R.raw.riv_keygen,
            ),
        onEvent = {},
    )
}
