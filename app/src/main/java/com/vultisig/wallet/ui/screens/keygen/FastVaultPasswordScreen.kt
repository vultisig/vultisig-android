package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordUiModel
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordViewModel
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultPasswordScreen(
    model: FastVaultPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    FastVaultPasswordScreen(
        title = stringResource(R.string.fast_vault_password_screen_title),
        state = state,
        passwordTextFieldState = model.passwordTextFieldState,
        confirmPasswordTextFieldState = model.confirmPasswordTextFieldState,
        onNextClick = model::navigateToHint,
        onBackClick = model::back,
        onShowMoreInfo = model::showMoreInfo,
        onHideMoreInfo = model::hideMoreInfo,
        onTogglePasswordVisibilityClick = model::togglePasswordVisibility,
        onToggleConfirmPasswordVisibilityClick = model::toggleConfirmPasswordVisibility,
    )
}

@Composable
internal fun FastVaultPasswordScreen(
    state: FastVaultPasswordUiModel,
    passwordTextFieldState: TextFieldState,
    confirmPasswordTextFieldState: TextFieldState,
    title: String,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onShowMoreInfo: () -> Unit,
    onHideMoreInfo: () -> Unit,
    onTogglePasswordVisibilityClick: () -> Unit,
    onToggleConfirmPasswordVisibilityClick: () -> Unit,
) {
    var hintBoxOffset by remember { mutableIntStateOf(0) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(it)
                    .padding(
                        top = 12.dp,
                        start = 24.dp,
                        end = 24.dp,
                    )
            ) {
                Text(
                    text = title,
                    style = Theme.brockmann.headings.largeTitle,
                    color = Theme.colors.text.primary,
                )
                UiSpacer(16.dp)

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    WarningCard(
                        onShowMoreInfo = onShowMoreInfo,
                        modifier = Modifier
                            .onGloballyPositioned { position ->
                                hintBoxOffset = position.boundsInRoot().bottom.toInt()
                            }
                    )

                    UiSpacer(8.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(
                            10.dp,
                            Alignment.CenterVertically
                        )
                    ) {
                        val focusRequester = remember {
                            FocusRequester()
                        }

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }

                        VsTextInputField(
                            textFieldState = passwordTextFieldState,
                            hint = stringResource(R.string.fast_vault_password_screen_password_hint),
                            trailingIcon = R.drawable.ic_question_mark,
                            type = VsTextInputFieldType.Password(
                                isVisible = state.isPasswordVisible,
                                onVisibilityClick = onTogglePasswordVisibilityClick
                            ),
                            focusRequester = focusRequester,
                            imeAction = ImeAction.Next,
                            modifier = Modifier
                                .testTag("FastVaultPasswordScreen.passwordField")
                        )

                        VsTextInputField(
                            textFieldState = confirmPasswordTextFieldState,
                            trailingIcon = R.drawable.ic_question_mark,
                            hint = stringResource(R.string.fast_vault_password_screen_reenter_password_hint),
                            type = VsTextInputFieldType.Password(
                                isVisible = state.isConfirmPasswordVisible,
                                onVisibilityClick = onToggleConfirmPasswordVisibilityClick
                            ),
                            innerState = state.innerState,
                            footNote = (state.errorMessage ?: UiText.Empty).asString(),
                            imeAction = if (state.isNextButtonEnabled) ImeAction.Go else ImeAction.None,
                            onKeyboardAction = {
                                onNextClick()
                            },
                            modifier = Modifier
                                .testTag("FastVaultPasswordScreen.confirmPasswordField")
                        )
                    }
                }
            }

            HintBox(
                title = stringResource(R.string.fast_vault_password_screen_hint_title),
                message = stringResource(R.string.fast_vault_password_screen_hint),
                offset = IntOffset(
                    x = 0,
                    y = hintBoxOffset - statusBarHeight
                ),
                pointerAlignment = Alignment.End,
                onDismissClick = onHideMoreInfo,
                modifier = Modifier
                    .padding(horizontal = 24.dp),
                isVisible = state.isMoreInfoVisible
            )

        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.fast_vault_password_screen_next),
                state = if (state.isNextButtonEnabled)
                    VsButtonState.Enabled else VsButtonState.Disabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .testTag("FastVaultPasswordScreen.next"),
                onClick = onNextClick,
            )
        }
    )
}

@Composable
private fun WarningCard(
    modifier: Modifier,
    onShowMoreInfo: () -> Unit,
) {
    val warningColor = Theme.colors.alerts.warning
    val lightWarningColor = Theme.colors.alerts.warning.copy(alpha = 0.25f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(lightWarningColor)
            .border(
                width = 1.dp,
                shape = RoundedCornerShape(12.dp),
                color = lightWarningColor
            )
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.fast_vault_password_screen_warning),
            style = Theme.brockmann.supplementary.footnote,
            color = warningColor,
            modifier = Modifier.weight(1f)
        )
        UiSpacer(
            size = 16.dp
        )
        UiIcon(
            R.drawable.alert,
            size = 16.dp,
            tint = warningColor,
            onClick = onShowMoreInfo
        )
    }
}

@Composable
@Preview
private fun FastVaultPasswordScreenPreview() {
    FastVaultPasswordScreen(
        state = FastVaultPasswordUiModel(
            isMoreInfoVisible = true
        ),
        passwordTextFieldState = rememberTextFieldState(),
        confirmPasswordTextFieldState = rememberTextFieldState(),
        title = stringResource(R.string.fast_vault_password_screen_title),
        onNextClick = {},
        onBackClick = {},
        onShowMoreInfo = {},
        onHideMoreInfo = {},
        onToggleConfirmPasswordVisibilityClick = {},
        onTogglePasswordVisibilityClick = {}
    )
}