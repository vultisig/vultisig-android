package com.vultisig.wallet.ui.screens.vault_settings.components.biometrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.SelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun BiometricsEnableScreen(
    navController: NavController,
    viewModel: BiometricsEnableViewModel = hiltViewModel()
) {
    val uiModel by viewModel.uiModel.collectAsState()
    BiometricsEnableScreen(
        uiModel = uiModel,
        navController = navController,
        passwordTextFieldState = viewModel.passwordTextFieldState,
        togglePasswordVisibility = viewModel::togglePasswordVisibility,
        onCheckChange = viewModel::onCheckChange,
        onSaveClick = viewModel::onSaveClick,
    )
}

@Composable
private fun BiometricsEnableScreen(
    uiModel: BiometricsEnableUiModel,
    navController: NavController,
    passwordTextFieldState: TextFieldState,
    togglePasswordVisibility: () -> Unit = {},
    onCheckChange: (Boolean) -> Unit = {},
    onSaveClick: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            Box(Modifier.imePadding()) {
                VsButton(
                    label = stringResource(id = R.string.vault_settings_enable_biometrics_save),
                    state = if (uiModel.isSaveEnabled)
                        VsButtonState.Enabled
                    else
                        VsButtonState.Disabled,
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                )
            }
        },
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.vault_settings_biometrics_screen_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            SelectionItem(
                title = stringResource(id = R.string.vault_settings_biometrics_screen_title),
                isChecked = uiModel.isSwitchEnabled,
                onCheckedChange = onCheckChange,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(id = R.string.vault_settings_biometrics_screen_description),
                style = Theme.montserrat.body1,
                color = Theme.colors.neutrals.n50,
            )
            UiSpacer(8.dp)
            FormBasicSecureTextField(
                hint = stringResource(R.string.vault_settings_biometrics_screen_password_placeholder),
                error = uiModel.passwordErrorMessage,
                isObfuscationMode = !uiModel.isPasswordVisible,
                textFieldState = passwordTextFieldState,
                onLostFocus = {},
                content = {
                    IconButton(onClick = togglePasswordVisibility) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(
                                id = if (uiModel.isPasswordVisible)
                                    R.drawable.visible else R.drawable.hidden
                            ),
                            contentDescription = "toggle password visibility"
                        )
                    }
                }
            )
            if (uiModel.passwordHint != null) {
                UiSpacer(size = 8.dp)
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = uiModel.passwordHint.asString(),
                    color = Theme.colors.neutrals.n50,
                    style = Theme.menlo.body2,
                )
            }
        }
    }
}

@Preview
@Composable
private fun BiometricsEnableScreenPreview() {
    BiometricsEnableScreen(
        uiModel = BiometricsEnableUiModel(),
        navController = rememberNavController(),
        passwordTextFieldState = TextFieldState()
    )
}