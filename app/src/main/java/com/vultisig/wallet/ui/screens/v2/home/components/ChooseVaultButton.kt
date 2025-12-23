package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.containers.VsContainerBorderType.*
import com.vultisig.wallet.ui.components.containers.VsContainerType.*
import com.vultisig.wallet.ui.components.containers.VsContainerCornerType
import com.vultisig.wallet.ui.components.containers.VsContainer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun ChooseVaultButton(
    modifier: Modifier = Modifier,
    vaultName: String,
    isFastVault: Boolean,
    onClick: () -> Unit,
) {
    VsContainer(
        modifier = modifier.clickOnce(onClick = onClick),
        vsContainerCornerType = VsContainerCornerType.Circular,
        borderType = Bordered(),
        type = SECONDARY
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = if (isFastVault) {
                    R.drawable.thunder
                } else {
                    R.drawable.ic_shield
                },
                contentDescription = "vault type logo",
                size = 16.dp,
                tint = if (isFastVault) Theme.colors.alerts.warning else Theme.colors.alerts.success,
            )
            UiSpacer(
                size = 6.dp
            )
            Text(
                text = vaultName,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 138.dp),
            )
            UiSpacer(
                size = 4.dp
            )
            UiIcon(
                drawableResId = R.drawable.ic_chevron_down_small,
                size = 16.dp
            )
        }
    }
}

@Preview
@Composable
private fun PreviewChooseVaultButton() {
    ChooseVaultButton(
        vaultName = "Main vault",
        isFastVault = false,
        onClick = {}
    )
}

@Preview
@Composable
private fun PreviewChooseVaultButton2() {
    ChooseVaultButton(
        vaultName = "Main vault very long name",
        isFastVault = false,
        onClick = {}
    )
}