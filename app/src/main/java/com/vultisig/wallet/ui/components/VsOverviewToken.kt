package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsOverviewToken(
    header: String,
    valuedToken: ValuedToken,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val token: Coin = valuedToken.token
    val chainLogo = token.chain.logo
    val value: String = valuedToken.value

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = shape,
            )
            .padding(
                horizontal = 16.dp,
                vertical = 24.dp,
            )
    ) {
        Text(
            text = header,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.colors.text.extraLight,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        UiSpacer(12.dp)

        Box {
            TokenLogo(
                logo = getCoinLogo(token.logo),
                title = token.ticker,
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = CircleShape,
                    )
                    .align(Alignment.Center),
                errorLogoModifier = Modifier
                    .size(36.dp)
            )

            chainLogo.takeIf { it != getCoinLogo(token.logo) }?.let {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.neutral100, CircleShape)
                        .border(
                            width = 2.dp,
                            color = Theme.colors.buttons.secondary,
                            shape = CircleShape
                        )
                        .align(BottomEnd)
                )
            }
        }

        UiSpacer(12.dp)

        val text = buildAnnotatedString {
            append(value)
            append(" ")
            withStyle(SpanStyle(color = Theme.colors.text.extraLight)) {
                append(token.ticker)
            }
        }

        Text(
            text = text,
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Text(
            text = valuedToken.fiatValue,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.colors.text.extraLight,
        )
    }
}