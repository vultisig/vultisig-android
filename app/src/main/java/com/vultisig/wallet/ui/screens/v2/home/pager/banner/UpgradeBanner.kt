package com.vultisig.wallet.ui.screens.v2.home.pager.banner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun UpgradeBanner(
    modifier: Modifier = Modifier,
    onUpgradeClick: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    all = 24.dp
                )
        ) {
            Text(
                text = stringResource(R.string.upgrade_banner_sign_faster),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
            UiSpacer(
                size = 2.dp
            )
            Text(
                text = stringResource(R.string.upgrade_banner_upgrade_your),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
            )
            UiSpacer(
                size = 16.dp
            )


            Text(
                text = stringResource(R.string.upgrade_banner_upgrade_now),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.colors.text.button.dark,
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .clickOnce(onClick = onUpgradeClick)
                    .background(
                        color = Theme.colors.aquamarine
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    )
            )
        }

        UiSpacer(
            weight = 1f
        )

        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.BottomEnd)
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.upgrade_vault_v2
                ),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier
                    .size(140.dp)
                    .align(alignment = Alignment.BottomEnd)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewUpgradeBanner() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {
        UpgradeBanner()
    }
}
