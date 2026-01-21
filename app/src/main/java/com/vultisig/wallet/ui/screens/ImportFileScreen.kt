package com.vultisig.wallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.models.FILE_ALLOWED_MIME_TYPES
import com.vultisig.wallet.ui.models.ImportFileState
import com.vultisig.wallet.ui.models.ImportFileViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignPasswordUiModel
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordBottomSheet
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordSheetContent
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.ActivityResultContractsGetContentWithMimeTypes
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun ImportFileScreen(
    viewModel: ImportFileViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiModel.collectAsState()
    val snackBarHostState = rememberVsSnackbarState()
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewModel.snackBarChannelFlow.collect { snackBarMessage ->
            snackBarMessage?.let {
                snackBarHostState.show(it.asString(context))
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
        onBackClick = viewModel::back,
        onImportVult = viewModel::importVult
    )
}


@Composable
private fun ImportFileScreen(
    uiModel: ImportFileState,
    onImportFile: () -> Unit = {},
    onImportVult: (AppZipEntry) -> Unit = {},
    onContinue: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onHidePasswordPromptDialog: () -> Unit = {},
    passwordTextFieldState: TextFieldState = TextFieldState(),
    onTogglePasswordVisibilityClick: () -> Unit = {},
    onConfirmPasswordClick: () -> Unit = {},
    snackBarHostState: VSSnackbarState = rememberVsSnackbarState()
) {
    if (uiModel.showPasswordPrompt) {
        KeysignPasswordBottomSheet(
            subtitle = stringResource(R.string.import_file_screen_enter_password_sub),
            confirmButtonLabel = stringResource(R.string.fast_vault_password_screen_next),
            state = KeysignPasswordUiModel(
                isPasswordVisible = !uiModel.isPasswordObfuscated,
                passwordError = uiModel.passwordErrorHint
            ),
            passwordFieldState = passwordTextFieldState,
            onPasswordVisibilityToggle = onTogglePasswordVisibilityClick,
            onContinueClick = onConfirmPasswordClick,
            onBackClick = onHidePasswordPromptDialog,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.import_file_screen_title),
        onBackClick = onBackClick,
        bottomBar = {
            if (uiModel.isZip == false)
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
                            horizontal = 12.dp,
                        )
                )
        }
    ) {
        Box {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize(),
            ) {

                Column(
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = when {
                                uiModel.error != null -> Theme.v2.colors.backgrounds.error
                                !uiModel.fileName.isNullOrBlank() -> Theme.v2.colors.backgrounds.success
                                else -> Theme.v2.colors.backgrounds.secondary
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        .dashedBorder(
                            width = 1.dp,
                            color = when {
                                uiModel.error != null -> Theme.v2.colors.alerts.error
                                !uiModel.fileName.isNullOrBlank() -> Theme.v2.colors.alerts.success
                                else -> Theme.v2.colors.border.normal
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
                            uiModel.error != null -> Theme.v2.colors.alerts.error
                            !uiModel.fileName.isNullOrBlank() -> Theme.v2.colors.alerts.success
                            else -> Theme.v2.colors.primary.accent4
                        },
                        modifier = Modifier
                            .size(48.dp)
                    )

                    Text(
                        text = uiModel.error?.asString()
                            ?: uiModel.fileName
                            ?: stringResource(R.string.import_file_import_your_vault_share),
                        color = when {
                            uiModel.error != null -> Theme.v2.colors.alerts.error
                            !uiModel.fileName.isNullOrBlank() -> Theme.v2.colors.alerts.success
                            else -> Theme.v2.colors.text.light
                        },
                        style = Theme.brockmann.headings.subtitle,
                        textAlign = TextAlign.Center,
                    )
                }

                UiSpacer(16.dp)

                if (uiModel.fileName.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.import_file_supported_file_types_dat_bak_vult),
                        color = Theme.v2.colors.text.extraLight,
                        style = Theme.brockmann.supplementary.footnote,
                    )
                }

                if (uiModel.isZip == true) {
                    ZipOutput(
                        zipOutputs = uiModel.zipOutputs,
                        onImportVult = onImportVult
                    )
                }

            }

            VsSnackBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                snackbarState = snackBarHostState
            )
        }

    }
}

@Composable
private fun ZipOutput(
    zipOutputs: List<AppZipEntry>,
    onImportVult: (AppZipEntry) -> Unit,
) {
    LazyColumn {
        itemsIndexed(zipOutputs) { index, zipOutput ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = zipOutput.name,
                        color = Theme.v2.colors.text.primary,
                        style = Theme.brockmann.supplementary.footnote,
                    )
                    UiSpacer(
                        weight = 1f
                    )
                    VsButton(
                        label = stringResource(R.string.import_file_screen_title),
                        size = VsButtonSize.Mini,
                        onClick = {
                            onImportVult(zipOutput)
                        },
                    )
                }

                if (zipOutputs.lastIndex != index) {
                    FadingHorizontalDivider()
                }
            }
        }

    }
}


@Preview(showBackground = true)
@Composable
private fun ImportFilePreview() {
    ImportFileScreen(
        uiModel = ImportFileState(isZip = true),
        snackBarHostState = rememberVsSnackbarState()
    )
}

@Preview(showBackground = true)
@Composable
private fun ImportFilePasswordPromptPreview() {
    ImportFileScreen(
        uiModel = ImportFileState(showPasswordPrompt = true),
        snackBarHostState = rememberVsSnackbarState()
    )
}