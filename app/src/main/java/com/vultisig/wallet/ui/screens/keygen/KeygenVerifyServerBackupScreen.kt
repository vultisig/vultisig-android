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
import com.vultisig.wallet.ui.models.keygen.KeygenVerifyServerBackupUiModel
import com.vultisig.wallet.ui.models.keygen.KeygenVerifyServerBackupViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeygenVerifyServerBackupScreen(
    navController: NavController,
    model: KeygenVerifyServerBackupViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    KeygenVerifyServerBackupScreen(
        navController = navController,
        state = state,
        codeFieldState = model.codeFieldState,
        onCodeLostFocus = { /* noop */ },
        onContinueClick = model::proceed,
    )
}

@Composable
internal fun KeygenVerifyServerBackupScreen(
    navController: NavController,
    state: KeygenVerifyServerBackupUiModel,
    codeFieldState: TextFieldState,
    onCodeLostFocus: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.keygen_verify_server_backup_title),
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
                    text = stringResource(R.string.keygen_verify_server_backup_caption),
                    style = Theme.montserrat.body1,
                    color = Theme.colors.neutral0
                )

                UiSpacer(size = 12.dp)

                FormTextFieldCard(
                    hint = stringResource(R.string.keygen_verify_server_backup_code_hint),
                    error = state.codeError,
                    keyboardType = KeyboardType.Email,
                    textFieldState = codeFieldState,
                    onLostFocus = onCodeLostFocus,
                )

                UiSpacer(size = 8.dp)
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
                    text = stringResource(R.string.keygen_verify_server_backup_warning)
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
private fun KeygenVerifyServerBackupScreenPreview() {
    KeygenVerifyServerBackupScreen(
        navController = rememberNavController(),
        state = KeygenVerifyServerBackupUiModel(),
        codeFieldState = TextFieldState(),
        onCodeLostFocus = {},
        onContinueClick = {},
    )
}