package com.vultisig.wallet.ui.screens.migration

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

/**
 * Lets the user say whether their VultiServer share is still hosted online by Vultisig, or whether
 * they have imported it onto another device of theirs. This prevents the GG20 → DKLS migration from
 * silently calling the online VultiServer when the share has been self-hosted.
 */
@Composable
internal fun MigrationServerShareLocationBottomSheet(
    onDismissRequest: () -> Unit,
    onUseOnlineVultiServer: () -> Unit,
    onUseAnotherDevice: () -> Unit,
) {
    VsModalBottomSheet(onDismissRequest = onDismissRequest) {
        Content(
            onUseOnlineVultiServer = onUseOnlineVultiServer,
            onUseAnotherDevice = onUseAnotherDevice,
        )
    }
}

@Composable
private fun Content(onUseOnlineVultiServer: () -> Unit, onUseAnotherDevice: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(24.dp)

        Text(
            text = stringResource(R.string.migration_server_share_location_title),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        FadingHorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        LocationOption(
            title = stringResource(R.string.migration_server_share_location_online_title),
            description =
                stringResource(R.string.migration_server_share_location_online_description),
            icon = R.drawable.settings_globe,
            onClick = onUseOnlineVultiServer,
            modifier = Modifier.testTag(MigrationServerShareLocationSheetTags.ONLINE),
        )

        UiSpacer(16.dp)

        LocationOption(
            title = stringResource(R.string.migration_server_share_location_another_device_title),
            description =
                stringResource(R.string.migration_server_share_location_another_device_description),
            icon = R.drawable.ic_devices,
            onClick = onUseAnotherDevice,
            modifier = Modifier.testTag(MigrationServerShareLocationSheetTags.ANOTHER_DEVICE),
        )

        UiSpacer(24.dp)
    }
}

@Composable
private fun LocationOption(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V2Container(
        modifier = modifier.fillMaxWidth().clickOnce(onClick = onClick),
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(color = Theme.v2.colors.border.normal),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            UiIcon(drawableResId = icon, size = 24.dp, tint = Theme.v2.colors.primary.accent4)

            UiSpacer(16.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(4.dp)

                Text(
                    text = description,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary,
                )
            }

            UiSpacer(12.dp)

            UiIcon(
                drawableResId = R.drawable.ic_caret_right,
                size = 20.dp,
                tint = Theme.v2.colors.text.secondary,
            )
        }
    }
}

object MigrationServerShareLocationSheetTags {
    const val ONLINE = "MigrationServerShareLocationSheet.online"
    const val ANOTHER_DEVICE = "MigrationServerShareLocationSheet.anotherDevice"
}
