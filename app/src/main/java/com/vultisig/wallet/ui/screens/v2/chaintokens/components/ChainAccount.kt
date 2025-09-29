package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainAccount(
    modifier: Modifier = Modifier,
    title: String,
    balance: String?,
    price: String?,
    fiatBalance: String?,
    isBalanceVisible: Boolean,
    tokenLogo: ImageModel,
    @DrawableRes chainLogo: Int?,
    onClick: () -> Unit = {},
    mergedBalance: String? = null,
) {
    Row(
        modifier = modifier
            .clickOnce(
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TokenLogo(
                logo = tokenLogo,
                title = title,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center),
                errorLogoModifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.neutral100),
            )
            chainLogo.takeIf { it != tokenLogo }?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.backgrounds.secondary,
                            shape = CircleShape
                        )
                        .align(Alignment.BottomEnd)
                )
            }
        }

        UiSpacer(
            size = 9.dp
        )

        Column {
            Text(
                text = title,
                style = Theme.brockmann.body.s.medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Theme.colors.text.primary
            )
            UiSpacer(
                size = 2.dp
            )

            V2Container(
                type = ContainerType.TERTIARY,
                borderType = ContainerBorderType.Borderless,
                cornerType = CornerType.RoundedCornerShape(
                    size = 8.dp
                )
            ) {
                LoadableValue(
                    value = price,
                    isVisible = isBalanceVisible,
                    style = Theme.satoshi.price.caption,
                    color = Theme.colors.text.light,
                    modifier = Modifier
                        .padding(
                            horizontal = 8.dp,
                            vertical = 3.dp
                        )
                )
            }
        }

        UiSpacer(
            weight = 1f
        )

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            LoadableValue(
                value = fiatBalance,
                isVisible = isBalanceVisible,
                style = Theme.satoshi.price.bodyS,
                color = Theme.colors.neutrals.n50,
            )

            UiSpacer(
                size = 4.dp
            )

            LoadableValue(
                value = balance,
                isVisible = isBalanceVisible,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
        }

        UiSpacer(
            size = 8.dp
        )

        UiIcon(
            drawableResId = R.drawable.ic_small_caret_right,
            size = 16.dp,
            tint = Theme.colors.text.primary,
        )
    }
}