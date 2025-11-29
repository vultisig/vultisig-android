package com.vultisig.wallet.ui.components.banners

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun UpgradeBanner(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = Theme.v2.colors.backgrounds.success,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Theme.v2.colors.alerts.success,
                shape = shape,
            )
            .padding(all = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_migration_upgrade),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
        )

        Text(
            text = stringResource(R.string.upgrade_banner_title),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.alerts.success,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun UpgradeBannerPreview() {
    UpgradeBanner()
}