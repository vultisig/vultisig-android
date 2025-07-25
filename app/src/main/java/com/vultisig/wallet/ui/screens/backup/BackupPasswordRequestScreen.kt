package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.MIME_TYPE_VAULT
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.RequestWriteFilePermissionOnceIfNotGranted
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument

@Composable
internal fun BackupPasswordRequestScreen(
    model: BackupPasswordRequestViewModel = hiltViewModel()
) {
    val state by model.state.collectAsState()

    RequestWriteFilePermissionOnceIfNotGranted(
        onRequestPermissionResult = model::handleWriteFilePermissionStatus,
    )

    RequestCreateDocument(
        mimeType = MIME_TYPE_VAULT,
        onDocumentCreated = model::saveVaultIntoUri,
        createDocumentRequestFlow = model.createDocumentRequestFlow,
    )

    val error = state.error
    if (error != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = error.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = model::dismissError,
        )
    }

    BackupPasswordRequestScreen(
        onNoPasswordClick = model::backupWithoutPassword,
        onWithPasswordClick = model::backupWithPassword,
        onBackClick = model::back,
    )
}

@Composable
internal fun BackupPasswordRequestScreen(
    onNoPasswordClick: () -> Unit,
    onWithPasswordClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick,
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        all = 24.dp,
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_passkeys),
                    contentDescription = null,
                    tint = Theme.colors.text.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = Theme.colors.backgrounds.tertiary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(
                            all = 16.dp,
                        )
                )

                UiSpacer(36.dp)

                Text(
                    text = stringResource(R.string.backup_password_request_title),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.colors.text.primary,
                    textAlign = TextAlign.Center,
                )

                UiSpacer(16.dp)

                Text(
                    text = stringResource(R.string.backup_password_request_description),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                    textAlign = TextAlign.Center,
                )

                UiSpacer(36.dp)

                VsButton(
                    label = stringResource(R.string.backup_password_request_no_password_action),
                    onClick = onNoPasswordClick,
                    variant = VsButtonVariant.Primary,
                    size = VsButtonSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD),
                )

                UiSpacer(12.dp)

                VsButton(
                    label = stringResource(R.string.backup_password_request_with_password_action),
                    onClick = onWithPasswordClick,
                    variant = VsButtonVariant.Secondary,
                    size = VsButtonSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }
        }
    )
}

@Preview
@Composable
private fun BackupPasswordRequestScreenPreview() {
    BackupPasswordRequestScreen(
        onNoPasswordClick = {},
        onWithPasswordClick = {},
        onBackClick = {},
    )
}

internal object BackupPasswordRequestScreenTags {
    const val BACKUP_WITHOUT_PASSWORD = "BackupPasswordRequestScreen.withoutPassword"
}