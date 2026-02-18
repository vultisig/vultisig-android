package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun SetupVaultGuideItem(){
    Row {
        UiIcon(
            drawableResId = R.drawable.icon_shield_solid,
            size = 24.dp,
            tint = Theme.v2.colors.alerts.info
        )
        UiSpacer(
            size = 16.dp
        )

        Column {
            Text(
                text = "No single point of failure",
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.neutrals.n50,
            )
            UiSpacer(
                size = 8.dp
            )
            Text(
                text = "One device alone can’t move funds. If one device is lost or exposed, it can’t approve on its own.",
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary
            )
        }
    }
}

@Preview
@Composable
private fun SetupVaultGuideItemPreview(){
    SetupVaultGuideItem()
}