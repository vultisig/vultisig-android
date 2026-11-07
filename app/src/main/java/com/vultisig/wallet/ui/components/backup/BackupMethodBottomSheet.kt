package com.vultisig.wallet.ui.components.backup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.SettingsModalBottomSheet
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

/**
 * Bottom sheet that presents the user with a choice between device (local file) backup and server
 * (email) backup.
 */
@Composable
internal fun BackupMethodBottomSheet(
    onDismissRequest: () -> Unit,
    onDeviceBackupClick: () -> Unit,
    onServerBackupClick: () -> Unit,
) {
    SettingsModalBottomSheet(onDismissRequest = onDismissRequest) {
        BackupMethodBottomSheetContent(
            onDeviceBackupClick = onDeviceBackupClick,
            onServerBackupClick = onServerBackupClick,
        )
    }
}

@Composable
private fun BackupMethodBottomSheetContent(
    onDeviceBackupClick: () -> Unit,
    onServerBackupClick: () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.backup_choose_method_title),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.neutrals.n50,
        )

        UiSpacer(16.dp)

        BackupOption(
            title = stringResource(R.string.backup_device_title),
            description = stringResource(R.string.backup_device_desc),
            icon = R.drawable.ic_laptop_mobile,
            onClick = onDeviceBackupClick,
        )

        UiSpacer(14.dp)

        BackupOption(
            title = stringResource(R.string.backup_server_title),
            description = stringResource(R.string.backup_server_desc),
            icon = R.drawable.cloud_download,
            onClick = onServerBackupClick,
        )
    }
}

@Composable
private fun BackupOption(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    V2Container(
        modifier = Modifier.fillMaxWidth().clickOnce(onClick = onClick),
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(color = Theme.v2.colors.border.light),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UiIcon(drawableResId = icon, size = 20.dp, tint = Theme.v2.colors.primary.accent4)

            UiSpacer(12.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Theme.brockmann.headings.subtitle,
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(4.dp)

                Text(
                    text = description,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }

            UiSpacer(12.dp)

            UiIcon(
                drawableResId = R.drawable.ic_small_caret_right,
                size = 16.dp,
                tint = Theme.v2.colors.text.tertiary,
            )
        }
    }
}
