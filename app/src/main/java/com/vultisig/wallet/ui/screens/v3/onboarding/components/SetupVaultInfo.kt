package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun SetupVaultInfo(
    onBack: () -> Unit,
) {
    V3Scaffold(
        onBackClick = onBack,
        applyDefaultPaddings = false,
    ) {
        Column(
            Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = V2Scaffold.PADDING_HORIZONTAL,
                        vertical = V2Scaffold.PADDING_HORIZONTAL,
                    )
            ) {
                Text(
                    text = "Your vault setup",
                    color = Theme.v2.colors.neutrals.n50,
                    style = Theme.brockmann.headings.title2
                )
                UiSpacer(
                    size = 20.dp,
                )
                SetupVaultHeader(
                    logo = com.vultisig.wallet.R.drawable.icon_shield_solid,
                    title = "Secure Vault",
                    subTitle = "2-device vault"
                )

                UiSpacer(
                    size = 14.dp,
                )
            }
            SetupVaultRive(
                modifier = Modifier
                    .align(alignment = Alignment.Start),
                animationRes = com.vultisig.wallet.R.raw.riv_choose_vault
            )
            UiSpacer(
                weight = 1f
            )

        }
    }
}


@Composable
@Preview
private fun SetupVaultInfoPreview() {
    SetupVaultInfo(
        onBack = {}
    )
}




