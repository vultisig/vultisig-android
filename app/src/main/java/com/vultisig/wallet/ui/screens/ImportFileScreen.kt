package com.vultisig.wallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiCustomContentAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.models.FILE_ALLOWED_MIME_TYPES
import com.vultisig.wallet.ui.models.ImportFileState
import com.vultisig.wallet.ui.models.ImportFileViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.ActivityResultContractsGetContentWithMimeTypes
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun ImportFileScreen(
    navController: NavHostController,
    viewModel: ImportFileViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiModel.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewModel.snackBarChannelFlow.collect { snackBarMessage ->
            snackBarMessage?.let {
                snackBarHostState.showSnackbar(it.asString(context))
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContractsGetContentWithMimeTypes(FILE_ALLOWED_MIME_TYPES)
        ) { uri: Uri? ->
            viewModel.fetchFileName(uri)
        }

    ImportFileScreen(
        navController = navController,
        uiModel = uiModel,
        passwordTextFieldState = viewModel.passwordTextFieldState,
        onImportFile = {
            launcher.launch("*/*")
        },
        onContinue = viewModel::saveFileToAppDir,
        snackBarHostState = snackBarHostState,
        onHidePasswordPromptDialog = viewModel::hidePasswordPromptDialog,
        onConfirmPasswordClick = viewModel::decryptVaultData,
        onTogglePasswordVisibilityClick = viewModel::togglePasswordVisibility,
    )
}


@Composable
private fun ImportFileScreen(
    navController: NavHostController,
    uiModel: ImportFileState,
    onImportFile: () -> Unit = {},
    onContinue: () -> Unit = {},
    onHidePasswordPromptDialog: () -> Unit = {},
    passwordTextFieldState: TextFieldState = TextFieldState(),
    onTogglePasswordVisibilityClick: () -> Unit = {},
    onConfirmPasswordClick: () -> Unit = {},
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    if (uiModel.showPasswordPrompt) {
        UiCustomContentAlertDialog {
            Column(horizontalAlignment = CenterHorizontally) {
                Text(
                    text = stringResource(id = R.string.import_file_screen_enter_password),
                    style = Theme.menlo.subtitle1,
                    color = Theme.colors.text.primary,
                )
                UiSpacer(size = 16.dp)
                FormBasicSecureTextField(
                    textFieldState = passwordTextFieldState,
                    hint = stringResource(R.string.import_file_screen_hint_password),
                    error = uiModel.passwordErrorHint,
                    onLostFocus = {},
                    isObfuscationMode = uiModel.isPasswordObfuscated,
                    content = {
                        IconButton(onClick = onTogglePasswordVisibilityClick) {
                            Icon(
                                modifier = Modifier.width(28.dp),
                                painter = painterResource(
                                    id = if (uiModel.isPasswordObfuscated)
                                        R.drawable.hidden else R.drawable.visible
                                ),
                                tint = Theme.colors.neutral0,
                                contentDescription = "change visibility button"
                            )
                        }
                    }
                )

                TextButton(onClick = onConfirmPasswordClick) {
                    Text(
                        text = stringResource(R.string.import_file_screen_ok),
                        style = Theme.menlo.subtitle1,
                        color = Theme.colors.neutral0
                    )
                }
                HorizontalDivider()
                TextButton(onClick = onHidePasswordPromptDialog) {
                    Text(
                        text = stringResource(R.string.import_file_screen_cancel),
                        style = Theme.menlo.subtitle1,
                        color = Theme.colors.neutral0
                    )
                }

            }
        }
    }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.import_file_screen_title),
                onBackClick = { navController.popBackStack() }
            )
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (uiModel.fileName.isNullOrBlank())
                    VsButtonState.Disabled
                else VsButtonState.Enabled,
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 24.dp,
                    )
            )
        },
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .padding(
                        all = 24.dp,
                    ),
            ) {

                Column(
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = when {
                                uiModel.error != null -> Theme.colors.backgrounds.error
                                !uiModel.fileName.isNullOrBlank() -> Theme.colors.backgrounds.success
                                else -> Theme.colors.backgrounds.neutral
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .dashedBorder(
                            width = 1.dp,
                            color = when {
                                uiModel.error != null -> Theme.colors.alerts.error
                                !uiModel.fileName.isNullOrBlank() -> Theme.colors.alerts.success
                                else -> Theme.colors.borders.normal
                            },
                            cornerRadius = 12.dp,
                            dashLength = 4.dp,
                            intervalLength = 4.dp,
                        )
                        .padding(
                            horizontal = 16.dp,
                            vertical = 48.dp,
                        )
                        .clickable(onClick = onImportFile)
                ) {

                    Icon(
                        painter = painterResource(
                            id = when {
                                !uiModel.fileName.isNullOrBlank() -> R.drawable.ic_page_check
                                else -> R.drawable.ic_cloud_upload
                            }
                        ),
                        contentDescription = null,
                        tint = when {
                            uiModel.error != null -> Theme.colors.alerts.error
                            !uiModel.fileName.isNullOrBlank() -> Theme.colors.alerts.success
                            else -> Theme.colors.primary.accent4
                        },
                        modifier = Modifier
                            .size(48.dp)
                    )

                    Text(
                        text = uiModel.error?.asString()
                            ?: uiModel.fileName
                            ?: "Import your vault share",
                        color = when {
                            uiModel.error != null -> Theme.colors.alerts.error
                            !uiModel.fileName.isNullOrBlank() -> Theme.colors.alerts.success
                            else -> Theme.colors.text.light
                        },
                        style = Theme.brockmann.headings.subtitle,
                        textAlign = TextAlign.Center,
                    )
                }

                UiSpacer(16.dp)

                if (uiModel.fileName.isNullOrBlank()) {
                    Text(
                        text = "Supported file types: .dat & .bak & .vult",
                        color = Theme.colors.text.extraLight,
                        style = Theme.brockmann.supplementary.footnote,
                    )
                }
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
private fun ImportFilePreview() {
    val navController = rememberNavController()
    ImportFileScreen(
        navController = navController,
        uiModel = ImportFileState(),
        snackBarHostState = SnackbarHostState()
    )
}

@Preview(showBackground = true)
@Composable
private fun ImportFilePasswordPromptPreview() {
    val navController = rememberNavController()
    ImportFileScreen(
        navController = navController,
        uiModel = ImportFileState(showPasswordPrompt = true),
        snackBarHostState = SnackbarHostState()
    )
}