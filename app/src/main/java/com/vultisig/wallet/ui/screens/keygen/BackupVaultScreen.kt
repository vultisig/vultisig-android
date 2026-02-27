package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.core.Fit
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.backup.MimeType
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v3.V3Icon
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.keygen.BackupVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument

@Composable
internal fun BackupVaultScreen(
    model: BackupVaultViewModel = hiltViewModel(),
) {

    RequestCreateDocument(
        mimeType =  MimeType.OCTET_STREAM.value,
        onDocumentCreated = model::saveContentToUriResult,
        createDocumentRequestFlow = model.createDocumentRequestFlow,
    )

    BackupVaultScreen(
        onBackupClick = model::backup,
    )
}

@Composable
private fun BackupVaultScreen(
    onBackupClick: () -> Unit,
) {
    var isNextEnabled by remember {
        mutableStateOf(false)
    }
    V3Scaffold(
        onBackClick = {},
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UiSpacer(70.dp)

                RiveAnimation(
                    animation = R.raw.riv_backup_vault_splash,
                    modifier = Modifier.size(
                        width = 266.dp,
                        height = 170.dp
                    ),
                    fit = Fit.COVER
                )

                UiSpacer(70.dp)

                V3Icon(
                    shinedBottom = Theme.v2.colors.alerts.info,
                    logo = R.drawable.arrow_cloude,
                )

                UiSpacer(24.dp)

                Text(
                    text = "Save backup to the cloud",
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )

                UiSpacer(
                    size = 32.dp
                )

                Text(
                    text = buildAnnotatedString {
                        append("Export this backup file, then save it to the cloude. it is")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Theme.v2.colors.neutrals.n50
                            )
                        ) {
                            append(" encrypted ")
                        }
                        append("with the password set earlier to unlock your Vault.\n")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = Theme.v2.colors.text.secondary
                            )
                        ) {
                            append(" Use a different cloud service or account for each backup. When you’re finished, delete the file from this device ")
                        }
                    },
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                VsCheckField(
                    modifier = Modifier
                        .testTag("SummaryScreen.agree"),
                    title = "I understand how to save this backup",
                    isChecked = isNextEnabled,
                    onCheckedChange = {
                        isNextEnabled = it
                    },
                )
                VsButton(
                    label = "Save backup",
                    iconLeft = R.drawable.ic_download,
                    onClick = onBackupClick,
                    state = VsButtonState.Enabled.takeIf { isNextEnabled }
                        ?: VsButtonState.Disabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 10.dp,
                        )
                        .testTag(BackupVaultScreenTags.BACKUP_NOW)
                )
            }
        }
    )

}

@Preview
@Composable
private fun BackupVaultScreenPreview() {
    BackupVaultScreen(
        onBackupClick = {},
    )
}

internal object BackupVaultScreenTags {
    const val BACKUP_NOW = "BackupVaultScreen.backup"
}