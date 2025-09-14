package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
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
        Row {
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
                    color = Theme.colors.text.primary,
                    modifier = Modifier
                        .clip(shape = CircleShape)
                        .background(
                            color = Theme.colors.aquamarine
                        )
                        .padding(
                            vertical = 8.dp,
                            horizontal = 16.dp
                        )
                )
            }


//            Box {
//                Image(
//                    painter = painterResource(
//                        id = com.vultisig.wallet.R.drawable.vultiserver
//                    ),
//                    contentDescription = null,
//                    modifier = Modifier.fillMaxSize(),
//                    contentScale = ContentScale.FillBounds
//                )
//                Text(text = "Aaslkdfj")
//                Box(
//                    modifier = modifier
//                        .size(48.dp)
//                        .clip(CircleShape)
//                        .background(
//                            Color.White.copy(alpha = 0.2f),
//                            CircleShape
//                        )
//                        .blur(radius = 1000.dp)
//                        .clickable {   },
//                    contentAlignment = Alignment.Center
//                ) {
//                    androidx.compose.material3.Icon(
//                        imageVector = Icons.Default.Close,
//                        contentDescription = null,
//                        tint = Color.Black,
//                        modifier = Modifier.size(24.dp)
//                    )
//                }
//            }

        }
    }
}

@Preview
@Composable
private fun PreviewUpgradeBanner() {
    UpgradeBanner()
}