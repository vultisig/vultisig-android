package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.core.Fit
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.BackupVaultViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BackupVaultScreen(
    model: BackupVaultViewModel = hiltViewModel(),
) {
    BackupVaultScreen(
        onBackupClick = model::backup,
    )
}

@Composable
private fun BackupVaultScreen(
    onBackupClick: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.backup_vault_topbar_title)
            )
        },
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        all = 24.dp,
                    )
            ) {
                UiSpacer(24.dp)

                RiveAnimation(
                    animation = R.raw.riv_backupvault_splash,
                    modifier = Modifier.fillMaxWidth(),
                    fit = Fit.COVER
                )

                UiSpacer(24.dp)

                Text(
                    text = stringResource(R.string.backup_vault_backup_vault_share_title),
                    style = Theme.brockmann.headings.title1,
                    color = Theme.colors.text.primary,
                    textAlign = TextAlign.Center,
                )

                val link = buildAnnotatedString {
                    append(stringResource(R.string.backup_vault_online_storage_is_recommended))
                    append(" ")
                    withLink(
                        link = LinkAnnotation.Url(
                            url = "https://docs.vultisig.com/vultisig-user-actions/managing-your-vault/vault-backup",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = Theme.colors.text.light,
                                    textDecoration = TextDecoration.Underline,
                                )
                            )
                        )
                    ) {
                        append(stringResource(R.string.feature_item_learn_more))
                    }
                }

                Text(
                    text = link,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                    textAlign = TextAlign.Center,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.backup_vault_backup_now_button),
                iconLeft = R.drawable.ic_download,
                onClick = onBackupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 10.dp,
                    )
                    .testTag(BackupVaultScreenTags.BACKUP_NOW)
            )
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