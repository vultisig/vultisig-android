package com.vultisig.wallet.ui.screens.v2.customtoken.components

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SearchedTokenInfo(
    token: Coin,
) {
    TopShineContainer {
        Row(
            verticalAlignment = Alignment.Companion.CenterVertically,
            modifier = Modifier.Companion
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                )
        ) {

            Box {
                TokenLogo(
                    logo = token.logo,
                    title = token.ticker,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center),
                    errorLogoModifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.backgrounds.body),
                )

                Image(
                    painter = painterResource(id = token.chain.monoToneLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.backgrounds.secondary,
                            shape = CircleShape
                        )
                        .background(
                            Theme.v2.colors.neutrals.n200,
                            CircleShape
                        )
                        .align(Alignment.BottomEnd)
                )
            }

            UiSpacer(
                size = 8.dp
            )

            Column {
                Row(
                    verticalAlignment = Alignment.Companion.Top,
                ) {
                    Text(
                        text = token.ticker,
                        color = Theme.v2.colors.text.primary,
                        style = Theme.brockmann.body.m.medium,
                    )

                    UiSpacer(
                        size = 6.dp
                    )

                    Text(
                        text = token.chain.raw,
                        color = Theme.v2.colors.text.secondary,
                        style = Theme.brockmann.supplementary.captionSmall,
                        modifier = Modifier.Companion
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.border.light,
                                shape = CircleShape
                            )
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
                    )
                }
                UiSpacer(
                    size = 6.dp
                )
                Text(
                    text = token.contractAddress,
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.supplementary.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
private fun SearchedTokenInfoPreview() {
    SearchedTokenInfo(
        token = Coins.Ethereum.GRT,
    )
}