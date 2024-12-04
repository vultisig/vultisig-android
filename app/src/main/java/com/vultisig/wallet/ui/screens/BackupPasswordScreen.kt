package com.vultisig.wallet.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.GradientInfoCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.BackupPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.WriteFilePermissionHandler
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.flow.receiveAsFlow


@Composable
internal fun BackupPasswordScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<BackupPasswordViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        WriteFilePermissionHandler(viewModel.permissionFlow, viewModel::onPermissionResult)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = "application/octet-stream"
        )
    ) { result ->
        result?.let { uri ->
            viewModel.saveContentToUriResult(uri, uiState.backupContent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveFileChannel.receiveAsFlow().collect { fileName ->
            filePickerLauncher.launch(fileName)
        }
    }

    val error = uiState.error
    if (error != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = error.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = viewModel::dismissError,
        )
    }

    Scaffold(
        bottomBar = {
            Column(
                Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                GradientInfoCard(
                    stringResource(id = R.string.backup_password_screen_warning),
                    Brush.vultiGradient()
                )
                MultiColorButton(
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
                        viewModel.backupEncryptedVault()
                    },
                )
                MultiColorButton(
                    text = stringResource(R.string.backup_password_screen_skip),
                    backgroundColor = Theme.colors.oxfordBlue800,
                    textColor = Theme.colors.turquoise800,
                    iconColor = Theme.colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    onClick = { viewModel.backupUnencryptedVault() }
                )
            }
        },
        topBar = {
            TopBar(
                navController = navHostController,
                centerText = stringResource(R.string.backup_password_screen_title),
                startIcon = R.drawable.ic_caret_left,
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
                    .verticalScroll(rememberScrollState())
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
                    content = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                painter = painterResource(
                                    id = if (uiState.isPasswordVisible)
                                        R.drawable.visible else R.drawable.hidden
                                ),
                                contentDescription = "toggle password visibility",
                                modifier = Modifier
                                    .size(
                                        20.dp,
                                        13.dp
                                    )
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
                    content = {
                        IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                            Icon(
                                painter = painterResource(
                                    id = if (uiState.isConfirmPasswordVisible)
                                        R.drawable.visible else R.drawable.hidden
                                ),
                                contentDescription = "toggle confirm password visibility",
                                modifier = Modifier
                                    .size(
                                        20.dp,
                                        13.dp
                                    )
                            )
                        }
                    })
            }
        }
    }
}