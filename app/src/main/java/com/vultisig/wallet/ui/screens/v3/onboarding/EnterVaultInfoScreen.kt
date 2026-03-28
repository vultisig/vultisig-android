package com.vultisig.wallet.ui.screens.v3.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.referral.AddReferralBottomSheet
import com.vultisig.wallet.ui.components.referral.AddReferralHeaderButton
import com.vultisig.wallet.ui.components.v2.modifiers.shinedBottom
import com.vultisig.wallet.ui.components.v2.utils.roundToPx
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoEvent
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoUiState
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoViewModel
import com.vultisig.wallet.ui.models.v3.onboarding.StepState
import com.vultisig.wallet.ui.models.v3.onboarding.StepType
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun EnterVaultInfoScreen(viewModel: EnterVaultInfoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showReferralSheet by rememberSaveable { mutableStateOf(false) }
    val referralCode by viewModel.referralCode.collectAsState()

    EnterVaultInfoScreen(
        uiState = uiState,
        hasReferral = !referralCode.isNullOrEmpty(),
        onReferralClick = { showReferralSheet = true },
        onEvent = viewModel::onEvent,
    )

    if (showReferralSheet) {
        AddReferralBottomSheet(
            onApply = { _ -> showReferralSheet = false },
            onDismissRequest = { showReferralSheet = false },
        )
    }
}

@Composable
internal fun EnterVaultInfoScreen(
    uiState: EnterVaultInfoUiState,
    hasReferral: Boolean = false,
    onReferralClick: () -> Unit = {},
    onEvent: (EnterVaultInfoEvent) -> Unit,
) {

    BackHandler { onEvent(EnterVaultInfoEvent.Back) }

    var hintBoxOffset by remember { mutableIntStateOf(0) }
    val scaffoldVerticalPadding = V3Scaffold.PADDING_VERTICAL.roundToPx()

    V3Scaffold(
        onBackClick = { onEvent(EnterVaultInfoEvent.Back) },
        actions = { AddReferralHeaderButton(hasReferral = hasReferral, onClick = onReferralClick) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VaultInfoSteps(stepsState = uiState.stepAndStates)
            UiSpacer(size = 32.dp)
            Text(
                text = uiState.activeStep.title.asString(),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(size = 12.dp)
            val infoIconId = "info_icon"
            val descText = uiState.activeStep.description.asString()
            val descHighlight = uiState.activeStep.descriptionHighlight?.asString()
            val descAnnotated = buildAnnotatedString {
                val start = if (descHighlight != null) descText.indexOf(descHighlight) else -1
                if (start >= 0) {
                    append(descText.substring(0, start))
                    withStyle(
                        SpanStyle(
                            color = Theme.v2.colors.neutrals.n50,
                            fontWeight = FontWeight.Bold,
                        )
                    ) {
                        append(descHighlight)
                    }
                    append(descText.substring(start + descHighlight!!.length))
                } else {
                    append(descText)
                }
                if (uiState.activeStep.showInfoIcon) {
                    append(" ")
                    appendInlineContent(infoIconId, "[i]")
                }
            }
            Text(
                text = descAnnotated,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
                inlineContent =
                    if (uiState.activeStep.showInfoIcon)
                        mapOf(
                            infoIconId to
                                InlineTextContent(
                                    placeholder =
                                        Placeholder(
                                            width = 16.sp,
                                            height = 16.sp,
                                            placeholderVerticalAlign =
                                                PlaceholderVerticalAlign.Center,
                                        )
                                ) {
                                    UiIcon(
                                        drawableResId = R.drawable.circleinfo,
                                        size = 16.dp,
                                        tint = Theme.v2.colors.text.tertiary,
                                        onClick = { onEvent(EnterVaultInfoEvent.ShowMoreInfo) },
                                    )
                                }
                        )
                    else emptyMap(),
                modifier =
                    Modifier.onGloballyPositioned { position ->
                        hintBoxOffset =
                            position.boundsInParent().bottom.toInt() + scaffoldVerticalPadding
                    },
            )
            UiSpacer(size = 24.dp)

            VsTextInputField(
                modifier = Modifier.testTag(EnterVaultInfoTags.INPUT_FIELD),
                textFieldState = uiState.inputTextFieldState,
                trailingIcon = R.drawable.close_circle,
                innerState = uiState.innerState,
                type =
                    if (uiState.activeStep.isPassword) {
                        VsTextInputFieldType.Password(
                            isVisible = uiState.isPasswordVisible,
                            onVisibilityClick = {
                                onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
                            },
                        )
                    } else {
                        VsTextInputFieldType.Text
                    },
                onTrailingIconClick = { onEvent(EnterVaultInfoEvent.ClearInput) },
                footNote = uiState.errorMessage?.asString(),
                imeAction = ImeAction.Go,
                onKeyboardAction = {
                    if (uiState.isNextButtonEnabled) {
                        onEvent(EnterVaultInfoEvent.Next)
                    }
                },
                hint = uiState.textFieldHint.asString(),
            )

            if (uiState.activeStep.isPassword) {
                UiSpacer(size = 8.dp)
                VsTextInputField(
                    modifier = Modifier.testTag(EnterVaultInfoTags.CONFIRM_PASSWORD_FIELD),
                    textFieldState = uiState.confirmPasswordTextFieldState,
                    type =
                        VsTextInputFieldType.Password(
                            isVisible = uiState.isConfirmPasswordVisible,
                            onVisibilityClick = {
                                onEvent(EnterVaultInfoEvent.ToggleConfirmPasswordVisibility)
                            },
                        ),
                    trailingIcon = R.drawable.close_circle,
                    innerState = uiState.innerState,
                    onTrailingIconClick = { onEvent(EnterVaultInfoEvent.ClearConfirmInput) },
                    imeAction = ImeAction.Go,
                    onKeyboardAction = {
                        if (uiState.isNextButtonEnabled) {
                            onEvent(EnterVaultInfoEvent.Next)
                        }
                    },
                    hint = uiState.confirmPasswordTextFieldHint.asString(),
                )
            }

            UiSpacer(weight = 1f)
            VsButton(
                label = stringResource(R.string.enter_email_screen_next),
                modifier = Modifier.fillMaxWidth().testTag(EnterVaultInfoTags.NEXT_BUTTON),
                state =
                    if (uiState.isNextButtonEnabled) VsButtonState.Enabled
                    else VsButtonState.Disabled,
            ) {
                onEvent(EnterVaultInfoEvent.Next)
            }
        }

        HintBox(
            message = stringResource(R.string.fast_vault_password_screen_hint),
            offset = IntOffset(x = 0, y = hintBoxOffset),
            pointerAlignment = Alignment.End,
            onDismissClick = { onEvent(EnterVaultInfoEvent.HideMoreInfo) },
            modifier = Modifier.padding(horizontal = 16.dp),
            isVisible = uiState.isMoreInfoVisible,
        )
    }
}

@Composable
private fun VaultInfoSteps(stepsState: Map<StepType, StepState>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stepsState.entries.forEachIndexed { index, (type, state) ->
            StepIcon(type = type, state = state)

            if (index != stepsState.size - 1) {
                StepSeparator()
            }
        }
    }
}

@Composable
private fun StepSeparator() {
    Box(
        modifier =
            Modifier.width(16.dp).height(1.dp).background(color = Theme.v2.colors.border.normal)
    )
}

@Composable
fun StepIcon(type: StepType, state: StepState) {

    Box(
        modifier =
            Modifier.size(44.dp)
                .clip(shape = CircleShape)
                .background(color = state.backgroundColor)
                .then(
                    if (state == StepState.Done) Modifier
                    else Modifier.shinedBottom(color = Theme.v2.colors.alerts.success)
                )
                .border(width = state.borderWidth, color = state.borderColor, shape = CircleShape),
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
private fun EnterVaultInfoScreenPreview() {
    EnterVaultInfoScreen(
        uiState =
            EnterVaultInfoUiState(
                stepAndStates =
                    mapOf(
                        StepType.Name to StepState.Done,
                        StepType.Email to StepState.Done,
                        StepType.Password to StepState.InProgress,
                    ),
                activeStep = StepType.Password,
                isMoreInfoVisible = true,
            ),
        onEvent = {},
    )
}

internal object EnterVaultInfoTags {
    const val INPUT_FIELD = "EnterVaultInfoScreen.inputField"
    const val CONFIRM_PASSWORD_FIELD = "EnterVaultInfoScreen.confirmPasswordField"
    const val NEXT_BUTTON = "EnterVaultInfoScreen.nextButton"
}
