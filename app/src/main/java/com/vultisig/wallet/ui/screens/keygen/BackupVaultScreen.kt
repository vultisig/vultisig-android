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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
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
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument

@Composable
internal fun BackupVaultScreen(model: BackupVaultViewModel = hiltViewModel()) {
    val title by model.title.collectAsState()

    RequestCreateDocument(
        mimeType = MimeType.OCTET_STREAM.value,
        onDocumentCreated = model::saveContentToUriResult,
        createDocumentRequestFlow = model.createDocumentRequestFlow,
    )

    BackupVaultScreen(
        title = title.asString(),
        isFastVault = model.isFastVault,
        onBackupClick = model::backup,
    )
}

@Composable
internal fun BackupVaultScreen(title: String, isFastVault: Boolean, onBackupClick: () -> Unit) {
    var isNextEnabled by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }
    V3Scaffold(
        onBackClick = {},
        content = {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UiSpacer(70.dp)

                RiveAnimation(
                    animation = R.raw.riv_backup_vault_splash,
                    modifier = Modifier.size(width = 266.dp, height = 170.dp),
                    fit = Fit.COVER,
                )

                UiSpacer(70.dp)

                V3Icon(shinedBottom = Theme.v2.colors.alerts.info, logo = R.drawable.arrow_cloude)

                UiSpacer(24.dp)

                Text(
                    text = title,
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )

                UiSpacer(size = 16.dp)

                Text(
                    text =
                        if (!isFastVault)
                            buildAnnotatedString {
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.tertiary)
                                ) {
                                    append(
                                        stringResource(R.string.backup_vault_screen_export_prefix)
                                    )
                                    append(" ")
                                }
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.secondary)
                                ) {
                                    append(
                                        stringResource(
                                            R.string
                                                .backup_export_this_backup_file_then_save_it_to_the_cloud
                                        )
                                    )
                                }
                            }
                        else {
                            buildAnnotatedString {
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.tertiary)
                                ) {
                                    append(
                                        stringResource(R.string.backup_vault_screen_export_prefix)
                                    )
                                }
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.primary)
                                ) {
                                    append(
                                        " ${stringResource(R.string.backup_vault_screen_encrypted)} "
                                    )
                                }
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.tertiary)
                                ) {
                                    append(
                                        stringResource(R.string.backup_vault_screen_password_suffix)
                                    )
                                }
                                append("\n")
                                withStyle(
                                    style =
                                        Theme.brockmann.body.s.medium
                                            .toSpanStyle()
                                            .copy(color = Theme.v2.colors.text.secondary)
                                ) {
                                    append(" ")
                                    append(stringResource(R.string.backup_vault_screen_cloud_tip))
                                    append(" ")
                                }
                            }
                        },
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VsCheckField(
                    modifier = Modifier.testTag("SummaryScreen.agree"),
                    title = stringResource(R.string.backup_i_understand_how_to_save_this_backup),
                    isChecked = isNextEnabled,
                    onCheckedChange = { isNextEnabled = it },
                )
                VsButton(
                    label = stringResource(R.string.backup_vault_screen_save_backup),
                    onClick = onBackupClick,
                    state =
                        VsButtonState.Enabled.takeIf { isNextEnabled } ?: VsButtonState.Disabled,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag(BackupVaultScreenTags.BACKUP_NOW),
                )
            }
        },
    )
}

@Preview
@Composable
private fun BackupVaultScreenPreview() {
    BackupVaultScreen(
        title = "Save backup 2 of 3 to the cloud",
        isFastVault = false,
        onBackupClick = {},
    )
}

internal object BackupVaultScreenTags {
    const val BACKUP_NOW = "BackupVaultScreen.backup"
}
