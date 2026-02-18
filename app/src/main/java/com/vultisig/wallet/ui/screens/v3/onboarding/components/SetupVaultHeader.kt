package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v3.V3Icon
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun SetupVaultHeader(
    logo: Int,
    title: String,
    subTitle: String,
){
    Box(
        modifier = Modifier
            .clip(
                shape = RoundedCornerShape(size = 16.dp)
            )
            .background(
                color = Theme.v2.colors.border.light
            )
            .padding(top = 1.dp)
    ) {

        Row(
            modifier = Modifier
                .clip(
                    shape = RoundedCornerShape(size = 16.dp)
                )

                .background(
                    color = Theme.v2.colors.backgrounds.secondary
                )
                .padding(
                    all = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V3Icon(logo)
            UiSpacer(
                size = 8.dp
            )
            Column() {
                Text(
                    text = title,
                    style = Theme.brockmann.headings.subtitle,
                    color = Theme.v2.colors.neutrals.n50,
                )
                Text(
                    text = subTitle,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            UiSpacer(
                size = 12.dp
            )
        }
    }
}

@Preview
@Composable
private fun SetupVaultHeaderPreview(){
    SetupVaultHeader(
        logo = R.drawable.icon_shield_solid,
        title = "Secure Vault",
        subTitle = "3-device vault"
    )
}