@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.form.VsUiCheckbox
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.theme.Theme

internal data class VaultIntroItem(
    val vaultId: String,
    val vaultName: String,
    val isEnabled: Boolean,
)

@Composable
internal fun NotificationsIntroBottomSheet(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    V2BottomSheet(
        onDismissRequest = onDismissRequest,
        displayDragHandler = false
    ) {
        NotificationsIntroBottomSheetContent(
            onEnable = onEnable,
            onNotNow = onNotNow
        )
    }
}

@Composable
internal fun NotificationsIntroBottomSheetContent(onEnable: () -> Unit, onNotNow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.nofitication_banner),
            contentDescription = stringResource(id = R.string.app_name),
        )

        UiSpacer(size = 34.dp)

        Text(
            text = stringResource(R.string.notifications_are_here),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 12.dp)

        Text(
            text = stringResource(R.string.notifications_description),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        UiSpacer(size = 32.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VsButton(
                label = stringResource(R.string.not_now),
                variant = VsButtonVariant.Secondary,
                onClick = onNotNow,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = stringResource(R.string.notifications_intro_enable),
                variant = VsButtonVariant.CTA,
                onClick = onEnable,
                modifier = Modifier.weight(1f),
            )
        }

        UiSpacer(size = 16.dp)
    }
}

@Composable
internal fun VaultNotificationOptInBottomSheet(
    vaults: List<VaultIntroItem>,
    onEnableVault: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    V2BottomSheet(onDismissRequest = onDismissRequest) {
        VaultNotificationOptInBottomSheetContent(
            vaults = vaults,
            onEnableVault = onEnableVault,
            onConfirm = onDismissRequest,
        )
    }
}

@Composable
internal fun VaultNotificationOptInBottomSheetContent(
    vaults: List<VaultIntroItem>,
    onEnableVault: (String, Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(
            horizontal = 16.dp,
            vertical = 24.dp
        )) {
        Text(
            text = stringResource(R.string.choose_vaults_for_notifications),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 16.dp)

        vaults.forEach { vault ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = vault.vaultName,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                    modifier = Modifier.weight(1f),
                )
                VsUiCheckbox(
                    checked = vault.isEnabled,
                    onCheckedChange = { enabled -> onEnableVault(vault.vaultId, enabled) },
                )
            }
        }

        UiSpacer(size = 24.dp)

        VsButton(
            label = stringResource(android.R.string.ok),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(size = 16.dp)
    }
}

@Preview
@Composable
private fun NotificationsIntroBottomSheetContentPreview() {
    V2BottomSheet(
        displayDragHandler = false
    ) { NotificationsIntroBottomSheetContent(onEnable = {}, onNotNow = {}) }
}

@Preview
@Composable
private fun VaultNotificationOptInBottomSheetContentPreview() {
    V2BottomSheet(
        displayDragHandler = false
    ) {
        VaultNotificationOptInBottomSheetContent(
            vaults =
                listOf(
                    VaultIntroItem(vaultId = "1", vaultName = "Main Vault", isEnabled = true),
                    VaultIntroItem(vaultId = "2", vaultName = "Savings Vault", isEnabled = false),
                    VaultIntroItem(vaultId = "3", vaultName = "Cold Storage", isEnabled = true),
                ),
            onEnableVault = { _, _ -> },
            onConfirm = {},
        )
    }
}
