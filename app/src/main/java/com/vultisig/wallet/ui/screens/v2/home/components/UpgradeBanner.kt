package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun UpgradeBanner(
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
) {

    V2Container(
        modifier = modifier
            .fillMaxWidth()
            .height(
                intrinsicSize = IntrinsicSize.Min
            ),
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    style = Theme.brockmann.body.s.medium,
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
                    .fillMaxSize()
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.upgrade_vault_v2
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .align(alignment = Alignment.BottomEnd)
                )

                VsCircleButton(
                    onClick = onCloseClick,
                    icon = R.drawable.glass,
                    size = VsCircleButtonSize.Custom(size = 40.dp),
                    type = VsCircleButtonType.Custom(
                        color = Theme.colors.neutrals.n100.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .align(alignment = Alignment.TopEnd)
                        .padding(
                            all = 8.dp
                        )
                )

            }
        }
    }
}

@Preview
@Composable
private fun PreviewUpgradeBanner() {
    UpgradeBanner()
}