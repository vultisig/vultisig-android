package com.vultisig.wallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiCustomContentAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.FormBasicSecureTextField
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
        onRemoveSelectedFile = viewModel::removeSelectedFile,
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
    onRemoveSelectedFile: () -> Unit = {},
    onContinue: () -> Unit = {},
    onHidePasswordPromptDialog: () -> Unit = {},
    passwordTextFieldState: TextFieldState = TextFieldState(),
    onTogglePasswordVisibilityClick: () -> Unit = {},
    onConfirmPasswordClick: () -> Unit = {},
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val appColor = Theme.colors
    val menloFamily = Theme.menlo

    Scaffold(snackbarHost = {
        SnackbarHost(snackBarHostState)
    }, bottomBar = {
        MultiColorButton(
            text = stringResource(R.string.send_continue_button),
            textColor = Theme.colors.oxfordBlue800,
            disabled = uiModel.fileName == null,
            minHeight = 44.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                ),
            onClick = onContinue,
        )
    }) {
        Box(Modifier.padding(it)) {

            UiBarContainer(
                navController = navController,
                title = stringResource(R.string.import_file_screen_title),
            ) {

                if (uiModel.showPasswordPrompt) {
                    UiCustomContentAlertDialog {
                        Column(horizontalAlignment = CenterHorizontally) {
                            Text(
                                text = stringResource(id = R.string.import_file_screen_enter_password),
                                style = Theme.menlo.subtitle1
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
                                                    R.drawable.visible else R.drawable.hidden
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

                Column(
                    horizontalAlignment = CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = 12.dp,
                        ),
                ) {
                    UiSpacer(size = 48.dp)

                    Text(
                        text = stringResource(R.string.import_file_screen_enter_your_previously_created_vault_share),
                        textAlign = TextAlign.Center,
                        color = appColor.neutral0,
                        style = menloFamily.body2
                    )

                    UiSpacer(size = 16.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Theme.colors.turquoise600Main.copy(alpha = 0.2f)
                            )
                            .clickOnce(onClick = onImportFile)
                            .drawBehind {
                                drawRoundRect(
                                    color = Color("#33e6bf".toColorInt()), style = Stroke(
                                        width = 8f,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(
                                                40f,
                                                20f
                                            ), 0f
                                        )
                                    ), cornerRadius = CornerRadius(16.dp.toPx())
                                )
                            }
                    ) {
                        Column(
                            horizontalAlignment = CenterHorizontally,
                            modifier = Modifier.align(Center),
                        ) {
                            Image(
                                painterResource(id = R.drawable.file),
                                contentDescription = "file"
                            )

                            UiSpacer(size = 24.dp)

                            Text(
                                text = stringResource(R.string.import_file_screen_upload_file_text_or_image),
                                color = appColor.neutral0,
                                style = menloFamily.body3,
                                textAlign = TextAlign.Center,
                            )
                        }

                    }
                    UiSpacer(size = 16.dp)

                    if (uiModel.fileName != null) {
                        Row(verticalAlignment = CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.file),
                                contentDescription = "file icon",
                                modifier = Modifier.width(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiModel.fileName,
                                color = appColor.neutral0,
                                style = menloFamily.body2,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            IconButton(onClick = clickOnce(onRemoveSelectedFile)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.x),
                                    contentDescription = "X",
                                    tint = appColor.neutral0
                                )
                            }

                        }
                    }

                    UiSpacer(weight = 1f)

                }
            }
        }
    }
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