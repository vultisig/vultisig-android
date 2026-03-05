@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.screens.v2.components.VsButton
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultNotificationSetupBottomSheet(
    vaultName: String,
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    V2BottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiSpacer(size = 16.dp)

            Text(
                text = stringResource(R.string.enable_push_notifications),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 12.dp)

            Text(
                text = stringResource(R.string.enable_notifications_for_vault, vaultName),
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.secondary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 24.dp)

            VsButton(
                label = stringResource(R.string.enable_push_notifications),
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.not_now),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
                modifier = Modifier.clickOnce(onClick = onNotNow),
            )

            UiSpacer(size = 16.dp)
        }
    }
}
