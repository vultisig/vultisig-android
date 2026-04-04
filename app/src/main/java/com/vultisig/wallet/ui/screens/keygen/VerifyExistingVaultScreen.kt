package com.vultisig.wallet.ui.screens.keygen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.modifiers.shinedBottom
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultEvent
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultStepState
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultStepType
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultUiState
import com.vultisig.wallet.ui.models.keygen.VerifyExistingVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifyExistingVaultScreen(
    viewModel: VerifyExistingVaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    VerifyExistingVaultScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun VerifyExistingVaultScreen(
    uiState: VerifyExistingVaultUiState,
    onEvent: (VerifyExistingVaultEvent) -> Unit,
) {
    val canProceed = uiState.isNextButtonEnabled && !uiState.isLoading

    BackHandler { onEvent(VerifyExistingVaultEvent.Back) }

    V3Scaffold(
        onBackClick = { onEvent(VerifyExistingVaultEvent.Back) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VerifyExistingVaultSteps(stepsState = uiState.stepAndStates)
            UiSpacer(size = 32.dp)
            Text(
                text = uiState.activeStep.title.asString(),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 12.dp)
            Text(
                text = uiState.activeStep.description.asString(),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
            )
            UiSpacer(size = 24.dp)

            VsTextInputField(
                modifier = Modifier.testTag(VerifyExistingVaultTags.INPUT_FIELD),
                textFieldState = uiState.inputTextFieldState,
                trailingIcon = R.drawable.close_circle,
                innerState = uiState.innerState,
                type = if (uiState.activeStep.isPassword) {
                    VsTextInputFieldType.Password(
                        isVisible = uiState.isPasswordVisible,
                        onVisibilityClick = {
                            onEvent(VerifyExistingVaultEvent.TogglePasswordVisibility)
                        },
                    )
                } else {
                    VsTextInputFieldType.Text
                },
                onTrailingIconClick = { onEvent(VerifyExistingVaultEvent.ClearInput) },
                footNote = uiState.errorMessage?.asString(),
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    if (canProceed) {
                        onEvent(VerifyExistingVaultEvent.Next)
                    }
                },
                hint = uiState.textFieldHint.asString(),
            )

            UiSpacer(weight = 1f)
            VsButton(
                label = stringResource(R.string.enter_email_screen_next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VerifyExistingVaultTags.NEXT_BUTTON),
                state = if (canProceed) VsButtonState.Enabled else VsButtonState.Disabled,
            ) {
                onEvent(VerifyExistingVaultEvent.Next)
            }
        }
    }
}

@Composable
private fun VerifyExistingVaultSteps(
    stepsState: Map<VerifyExistingVaultStepType, VerifyExistingVaultStepState>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stepsState.entries.forEachIndexed { index, (type, state) ->
            VerifyExistingVaultStepIcon(type = type, state = state)

            if (index != stepsState.size - 1) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(color = Theme.v2.colors.border.normal)
                )
            }
        }
    }
}

@Composable
private fun VerifyExistingVaultStepIcon(
    type: VerifyExistingVaultStepType,
    state: VerifyExistingVaultStepState,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape = CircleShape)
            .background(color = state.backgroundColor)
            .then(
                if (state == VerifyExistingVaultStepState.Done) Modifier
                else Modifier.shinedBottom(color = Theme.v2.colors.alerts.success)
            )
            .border(
                width = state.borderWidth,
                color = state.borderColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(
            drawableResId = type.logo,
            tint = state.logoTint,
            contentDescription = null,
            size = 18.dp,
        )
    }
}

@Preview
@Composable
private fun VerifyExistingVaultScreenEmailPreview() {
    VerifyExistingVaultScreen(
        uiState = VerifyExistingVaultUiState(
            stepAndStates = mapOf(
                VerifyExistingVaultStepType.Email to VerifyExistingVaultStepState.InProgress,
                VerifyExistingVaultStepType.Password to VerifyExistingVaultStepState.Inactive,
            ),
            activeStep = VerifyExistingVaultStepType.Email,
        ),
        onEvent = {},
    )
}


@Preview
@Composable
private fun VerifyExistingVaultScreenPasswordPreview() {
    VerifyExistingVaultScreen(
        uiState = VerifyExistingVaultUiState(
            stepAndStates = mapOf(
                VerifyExistingVaultStepType.Email to VerifyExistingVaultStepState.Done,
                VerifyExistingVaultStepType.Password to VerifyExistingVaultStepState.InProgress,
            ),
            activeStep = VerifyExistingVaultStepType.Password,
        ),
        onEvent = {},
    )
}


internal object VerifyExistingVaultTags {
    const val INPUT_FIELD = "VerifyExistingVaultScreen.inputField"
    const val NEXT_BUTTON = "VerifyExistingVaultScreen.nextButton"
}
