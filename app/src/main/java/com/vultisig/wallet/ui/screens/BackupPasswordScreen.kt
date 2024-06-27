package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.models.BackupPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BackupPasswordScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<BackupPasswordViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        bottomBar = {
            Column(Modifier.imePadding()) {
                MultiColorButton(
                    minHeight = 44.dp,
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    text = stringResource(R.string.backup_password_screen_save),
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.backupVault()
                    },
                )
                MultiColorButton(
                    text = stringResource(R.string.backup_password_screen_skip),
                    backgroundColor = Theme.colors.oxfordBlue800,
                    textColor = Theme.colors.turquoise800,
                    iconColor = Theme.colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    minHeight = 44.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        )
                ) {
                    viewModel.backupVaultSkipPassword()
                }
            }
        },
        topBar = {
            TopBar(
                navController = navHostController,
                centerText = stringResource(R.string.backup_password_screen_title),
                startIcon = R.drawable.caret_left,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .background(Theme.colors.oxfordBlue800),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
            ) {

                Text(
                    stringResource(
                        R.string.backup_password_optional_password_protection
                    ),
                    style = Theme.montserrat.body1,
                    color = Theme.colors.neutral0
                )

                UiSpacer(size = 12.dp)
                FormBasicSecureTextField(
                    hint = stringResource(R.string.backup_password_screen_enter_password),
                    error = uiState.passwordErrorMessage,
                    isObfuscationMode = !uiState.isPasswordVisible,
                    textFieldState = viewModel.passwordTextFieldState,
                    onLostFocus = {},
                    actions = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                painter = painterResource(
                                    id = if (uiState.isPasswordVisible)
                                        R.drawable.hidden else R.drawable.visible
                                ),
                                contentDescription = "toggle password visibility"
                            )
                        }
                    })
                UiSpacer(size = 8.dp)
                FormBasicSecureTextField(
                    hint = stringResource(R.string.backup_password_screen_verify_password),
                    error = uiState.confirmPasswordErrorMessage,
                    isObfuscationMode = !uiState.isConfirmPasswordVisible,
                    textFieldState = viewModel.confirmPasswordTextFieldState,
                    onLostFocus = viewModel::validateConfirmPassword,
                    actions = {
                        IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                            Icon(
                                painter = painterResource(
                                    id = if (uiState.isConfirmPasswordVisible)
                                        R.drawable.hidden else R.drawable.visible
                                ),
                                contentDescription = "toggle confirm password visibility"
                            )
                        }
                    })
            }
        }
    }
}
