package com.vultisig.wallet.ui.screens.v2.home.pager.container

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.UpgradeBanner
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun HomePagePagerContainer(
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {},
    containerType: ContainerType = ContainerType.SECONDARY,
    content: @Composable () -> Unit,
) {
    V2Container(
        modifier = modifier
            .fillMaxWidth(),
        type = containerType,
        borderType = ContainerBorderType.Bordered()
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            content()
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


@Preview
@Composable
fun HomePagePagerContainerPreview() {
    HomePagePagerContainer {
        UpgradeBanner {  }
    }
}

