package com.vultisig.wallet.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingEvent
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingUiModel
import com.vultisig.wallet.ui.models.onboarding.VaultBackupOnboardingViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VaultBackupOnboardingScreen(
    model: VaultBackupOnboardingViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    BlockBackClick()

    VaultBackupOnboardingScreen(
        uiState = state,
        onEvent = model::onEvent,
    )
}

@Composable
internal fun VaultBackupOnboardingScreen(
    uiState: VaultBackupOnboardingUiModel,
    onEvent: (VaultBackupOnboardingEvent) -> Unit,
) {
    V3Scaffold(
        onBackClick = {
            onEvent(VaultBackupOnboardingEvent.Back)
        },
        applyDefaultPaddings = false,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                RiveAnimation(
                    animation = uiState.rive,
                    modifier = Modifier
                        .size(
                            width = 350.dp,
                            height = 260.dp,
                        )
                        .background(
                            color = Theme.v2.colors.backgrounds.secondary
                        )
                        .dashedBorder(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            dashLength = 4.dp,
                            intervalLength = 4.dp,
                            cornerRadius = 0.dp
                        )
                )
                UiSpacer(
                    size = 14.dp
                )

                Column(
                    modifier = Modifier
                        .padding(
                            horizontal = V3Scaffold.PADDING_HORIZONTAL,
                            vertical = V3Scaffold.PADDING_VERTICAL,
                        )
                ) {
                    GradientTitleText(
                        gradientPart = stringResource(R.string.backup_backups),
                        regularPart = stringResource(R.string.backup_your_new_recovery_method),
                        style = Theme.brockmann.headings.title2
                    )
                    UiSpacer(
                        size = 16.dp
                    )

                    Text(
                        text = stringResource(R.string.backup_backups_power_your_vault),
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.tertiary
                    )
                    UiSpacer(
                        size = 32.dp
                    )

                    uiState.tips.forEach { (title, description, logo) ->

                        Row {
                            UiIcon(
                                drawableResId =  logo,
                                contentDescription = null,
                                size = 24.dp,
                                tint = Theme.v2.colors.alerts.info,
                            )
                            UiSpacer(
                                size = 16.dp
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = title.asString(),
                                    style = Theme.brockmann.headings.subtitle,
                                    color = Theme.v2.colors.neutrals.n50,
                                )
                                UiSpacer(
                                    size = 4.dp
                                )

                                Text(
                                    text = description.asString(),
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.tertiary,
                                )

                            }
                        }
                        UiSpacer(size = 16.dp)
                    }

                    UiSpacer(
                        weight = 1f
                    )

                    VsButton(
                        label = stringResource(R.string.vault_setup_i_understand),
                        onClick = {
                            onEvent(VaultBackupOnboardingEvent.Next)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )


                }

            }
        }
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
        text = buildAnnotatedString {
            withStyle(SpanStyle(brush = Theme.v2.colors.gradients.primary)) {
                append(gradientPart)
            }
            withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) {
                append(regularPart)
            }
        },
        style = style,
    )
}


@Preview
@Composable
private fun VaultBackupOnboardingScreenPreview() {
    VaultBackupOnboardingScreen(
        uiState = VaultBackupOnboardingUiModel(
            tips = VaultBackupOnboardingViewModel.FastVaultBackupOnboardingTips,
            rive = R.raw.riv_keygen,
        ),
        onEvent = {}
    )
}