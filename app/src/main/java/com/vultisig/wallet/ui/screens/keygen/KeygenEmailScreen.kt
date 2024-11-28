package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.keygen.KeygenEmailUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenEmailViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeygenEmailScreen(
    navController: NavController,
    model: KeygenEmailViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    KeygenEmailScreen(
        navController = navController,
        state = state,
        emailFieldState = model.emailFieldState,
        verifyEmailFieldState = model.verifyEmailFieldState,
        onEmailLostFocus = model::verifyEmail,
        onVerifyEmailLostFocus = model::verifyEmailDouble,
        onReceiveAlertsCheck = model::receiveAlerts,
        onContinueClick = model::proceed,
    )
}

@Composable
private fun KeygenEmailScreen(
    navController: NavController,
    state: KeygenEmailUiModel,
    emailFieldState: TextFieldState,
    verifyEmailFieldState: TextFieldState,
    onEmailLostFocus: () -> Unit,
    onVerifyEmailLostFocus: () -> Unit,
    onReceiveAlertsCheck: (Boolean) -> Unit,
    onContinueClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.keygen_email_screen_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .background(Theme.colors.oxfordBlue800)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
            ) {
                UiSpacer(size = 8.dp)

                Text(
                    stringResource(R.string.keygen_email_caption),
                    style = Theme.montserrat.body1,
                    color = Theme.colors.neutral0
                )

                UiSpacer(size = 12.dp)

                FormTextFieldCard(
                    hint = stringResource(R.string.keygen_email_input_hint),
                    error = state.emailError,
                    keyboardType = KeyboardType.Email,
                    textFieldState = emailFieldState,
                    onLostFocus = onEmailLostFocus,
                )

                UiSpacer(size = 8.dp)

                FormTextFieldCard(
                    hint = stringResource(R.string.keygen_verify_email_hint),
                    error = state.verifyEmailError,
                    keyboardType = KeyboardType.Email,
                    textFieldState = verifyEmailFieldState,
                    onLostFocus = onVerifyEmailLostFocus,
                )

                UiSpacer(size = 12.dp)

//                Row {
//                    Text(
//                        text = stringResource(R.string.keygen_email_transactions_caption),
//                        color = Theme.colors.neutral0,
//                        style = Theme.montserrat.body1,
//                        modifier = Modifier.weight(1f),
//                    )
//
//                    UiSpacer(size = 12.dp)
//
//                    VaultSwitch(
//                        checked = state.shouldReceiveAlerts,
//                        onCheckedChange = onReceiveAlertsCheck,
//                        colors = SwitchDefaults.colors(
//                            checkedThumbColor = Theme.colors.neutral0,
//                            checkedBorderColor = Theme.colors.turquoise800,
//                            checkedTrackColor = Theme.colors.turquoise800,
//                            uncheckedThumbColor = Theme.colors.neutral0,
//                            uncheckedBorderColor = Theme.colors.oxfordBlue400,
//                            uncheckedTrackColor = Theme.colors.oxfordBlue400
//                        ),
//                    )
//                }
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .imePadding()
                    .padding(
                        all = 16.dp,
                    )
            ) {
                GradientInfoCard(
                    text = stringResource(R.string.keygen_fast_vault_email_hint)
                )

                MultiColorButton(
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(R.string.keygen_email_continue_button),
                    onClick = onContinueClick,
                )
            }
        },
    )
}

@Preview
@Composable
private fun KeygenEmailScreenPreview() {
    KeygenEmailScreen(
        navController = rememberNavController(),
        state = KeygenEmailUiModel(),
        emailFieldState = TextFieldState(),
        verifyEmailFieldState = TextFieldState(),
        onEmailLostFocus = {},
        onVerifyEmailLostFocus = {},
        onReceiveAlertsCheck = {},
        onContinueClick = {},
    )
}