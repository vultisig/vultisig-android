package com.vultisig.wallet.ui.screens.v3.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.modifiers.shinedBottom
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoEvent
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoUiState
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoViewModel
import com.vultisig.wallet.ui.models.v3.onboarding.StepState
import com.vultisig.wallet.ui.models.v3.onboarding.StepType
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString


@Composable
internal fun EnterVaultInfoScreen(
    viewModel: EnterVaultInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    EnterVaultInfoScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}


@Composable
internal fun EnterVaultInfoScreen(
    uiState: EnterVaultInfoUiState,
    onEvent: (EnterVaultInfoEvent) -> Unit,
) {
    V3Scaffold(
        onBackClick = {
            onEvent(EnterVaultInfoEvent.Back)
        },
        actions = {
            Text(
                modifier = Modifier
                    .clip(
                        shape = RoundedCornerShape(size = 16.dp)
                    )
                    .background(
                        color = Theme.v2.colors.backgrounds.surface2
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = "Add referral",
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.neutrals.n50,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VaultInfoSteps(
                stepsState = uiState.stepAndStates,
            )
            UiSpacer(
                size = 32.dp
            )
            Text(
                text = uiState.activeStep.title.asString(),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(
                size = 12.dp
            )
            val descText = uiState.activeStep.description.asString()
            val descHighlight = uiState.activeStep.descriptionHighlight?.asString()
            val descAnnotated = buildAnnotatedString {
                val start = if (descHighlight != null) descText.indexOf(descHighlight) else -1
                if (start >= 0) {
                    append(descText.substring(0, start))
                    withStyle(SpanStyle(color = Theme.v2.colors.neutrals.n50, fontWeight = FontWeight.Bold)) {
                        append(descHighlight)
                    }
                    append(descText.substring(start + descHighlight!!.length))
                } else {
                    append(descText)
                }
            }
            Text(
                text = descAnnotated,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
            )
            UiSpacer(
                size = 24.dp
            )


            VsTextInputField(
                modifier = Modifier.testTag(EnterVaultInfoTags.INPUT_FIELD),
                textFieldState = uiState.inputTextFieldState,
                trailingIcon = R.drawable.close_circle,
                innerState = uiState.innerState,
                type = if (uiState.activeStep.isPassword) {
                    VsTextInputFieldType.Password(
                        isVisible = uiState.isPasswordVisible,
                        onVisibilityClick = {
                            onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
                        }
                    )
                } else {
                    VsTextInputFieldType.Text
                },
                onTrailingIconClick = {
                    onEvent(EnterVaultInfoEvent.ClearInput)
                },
                footNote = uiState.errorMessage?.asString(),
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    onEvent(EnterVaultInfoEvent.Next)
                },
            )

            if (uiState.activeStep.isPassword) {
                UiSpacer(
                    size = 8.dp
                )
                VsTextInputField(
                    modifier = Modifier.testTag(EnterVaultInfoTags.CONFIRM_PASSWORD_FIELD),
                    textFieldState = uiState.confirmPasswordTextFieldState,
                    type = VsTextInputFieldType.Password(
                        isVisible = uiState.isConfirmPasswordVisible,
                        onVisibilityClick = {
                            onEvent(EnterVaultInfoEvent.ToggleConfirmPasswordVisibility)
                        }
                    ),

                    trailingIcon = R.drawable.close_circle,
                    innerState = uiState.innerState,
                    onTrailingIconClick = {
                        onEvent(EnterVaultInfoEvent.ClearInput)
                    },
                    imeAction = ImeAction.Go,
                    onKeyboardAction = {
                        onEvent(EnterVaultInfoEvent.Next)
                    },
                )
            }

            UiSpacer(
                weight = 1f
            )
            VsButton(
                label = "Next",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EnterVaultInfoTags.NEXT_BUTTON)
            ) {
                onEvent(EnterVaultInfoEvent.Next)
            }
        }
    }
}

@Composable
private fun VaultInfoSteps(
    stepsState: Map<StepType, StepState>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stepsState.entries.forEachIndexed { index, (type, state) ->
            StepIcon(
                type = type,
                state = state,
            )

            if (index != stepsState.size - 1) {
                StepSeparator()
            }
        }
    }
}

@Composable
private fun StepSeparator() {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(1.dp)
            .background(
                color = Theme.v2.colors.border.normal
            )
    )
}

@Composable
fun StepIcon(
    type: StepType,
    state: StepState
) {

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(
                shape = CircleShape
            )
            .background(
                color = state.backgroundColor
            )
            .then(
                if (state == StepState.Done) Modifier
                else Modifier.shinedBottom(
                    color = Theme.v2.colors.alerts.success
                )
            )
            .border(
                width = state.borderWidth,
                color = state.borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(type.logo),
            modifier = Modifier
                .size(18.dp),
            contentDescription = null,
        )
    }
}


@Preview
@Composable
private fun EnterVaultInfoScreenPreview() {
    EnterVaultInfoScreen(
        uiState = EnterVaultInfoUiState(
            stepAndStates = mapOf(
                StepType.Name to StepState.Done,
                StepType.Email to StepState.InProgress,
                StepType.Password to StepState.Inactive,
            ),
            activeStep = StepType.Email
        ),
        onEvent = {}
    )
}

internal object EnterVaultInfoTags {
    const val INPUT_FIELD = "EnterVaultInfoScreen.inputField"
    const val CONFIRM_PASSWORD_FIELD = "EnterVaultInfoScreen.confirmPasswordField"
    const val NEXT_BUTTON = "EnterVaultInfoScreen.nextButton"
}