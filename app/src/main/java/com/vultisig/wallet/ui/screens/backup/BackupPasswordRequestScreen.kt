package com.vultisig.wallet.ui.screens.backup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.MIME_TYPE_VAULT
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.containers.V2Scaffold
import com.vultisig.wallet.ui.components.v2.highlightedText
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



@Composable
private fun BackupPasswordRequestScreen(
    onNoPasswordClick : () -> Unit,
    onWithPasswordClick : () -> Unit,
    onBackClick : () -> Unit,
) {
    V2Scaffold(
        onBackClick = onBackClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            UiSpacer(
                size = 14.dp,
            )

            UiIcon(
                modifier = Modifier
                    .size(
                        64.dp,
                    )
                    .background(
                        color = Theme.colors.backgrounds.tertiary,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(
                        all = 16.dp,
                    ),
                drawableResId = R.drawable.ic_passkeys,
                size = 32.dp
            )

            UiSpacer(
                size = 36.dp
            )

            Text(
                text = "Do you want to encrypt your backup with a password?",
                style = Theme.brockmann.headings.title2,
                color = Theme.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(
                size = 16.dp,
            )

            BackupCaution(
                icon = R.drawable.backup_passowrd_lock,
                mainText = "If you choose to add a password, this  will be used to encrypt the backup file.",
                highlightedWords = listOf("encrypt")
            )

            UiSpacer(
                size = 16.dp,
            )
            BackupCaution(
                icon = R.drawable.encript,
                mainText = "Remember: if you forget your vault password, it cannot be reset or recovered.",
                highlightedWords = listOf("cannot")
            )

            UiSpacer(
                size = 16.dp,
            )
            BackupCaution(
                icon = R.drawable.remeber,
                mainText = "By default, your backup is secure without an extra password, since you store Vault shares in different locations.",
                highlightedWords = listOf("secure without")
            )

            UiSpacer(
                weight = 1f
            )

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
}


@Composable
private fun BackupCaution(
    mainText: String,
    highlightedWords: List<String>,
    @DrawableRes icon: Int
) {

    V2Container(
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    all = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = icon,
                size = 20.dp,
                tint = Theme.colors.primary.accent4,
            )
            UiSpacer(16.dp)

            Text(
                text = highlightedText(
                    mainText = mainText,
                    highlightedWords = highlightedWords,
                    mainTextColor = Theme.colors.text.extraLight,
                    mainTextStyle = Theme.brockmann.supplementary.footnote,
                    highlightTextColor = Theme.colors.text.primary,
                    highlightTextStyle = Theme.brockmann.supplementary.footnote,
                ),
                modifier = Modifier
                    .weight(1f),
            )
        }
    }
}

@Preview
@Composable
private fun BackupCautionPreview() {
    BackupCaution(
        icon = R.drawable.device_backup,
        mainText = "By default, your backup is secure without an extra password, since you store Vault shares in different locations.",
        highlightedWords = listOf("secure without")
    )
}





