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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsOverviewToken(
    header: String,
    valuedToken: ValuedToken,
    shape: Shape,
    modifier: Modifier = Modifier,
    withContainer: Boolean = true,
) {
    val token: Coin = valuedToken.token

    VsOverviewToken(
        header = header,
        tokenLogo = getCoinLogo(token.logo),
        ticker = token.ticker,
        chainLogo =
            token.chain.monoToneLogo.takeIf { !token.isNativeToken || token.chain.isLayer2 },
        value = valuedToken.value,
        fiatValue = valuedToken.fiatValue,
        shape = shape,
        modifier = modifier,
        withContainer = withContainer,
    )
}

/**
 * The same card addressed by its display parts rather than by a [ValuedToken].
 *
 * Used where the asset shown is not the transaction's own coin — the done screen's decoder-driven
 * hero resolves its asset from the signed units, which may be a chain-native denom the payload
 * never named. [chainLogo] is null there because the resolved asset carries no payload chain badge.
 *
 * An empty [value] renders the ticker alone, for a reading that identifies the asset but states no
 * truthful quantity. [scope] is what the transaction committed to in words — "50% of your staked
 * position" — which stays exact even when the settled amount is only an estimate, so it is the last
 * thing to be dropped rather than the first.
 */
@Composable
internal fun VsOverviewToken(
    header: String,
    tokenLogo: ImageModel,
    ticker: String,
    chainLogo: Int?,
    value: String,
    fiatValue: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    withContainer: Boolean = true,
    scope: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            if (withContainer) {
                modifier
                    .background(color = Theme.v2.colors.backgrounds.secondary, shape = shape)
                    .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            } else {
                modifier
            },
    ) {
        Text(
            text = header,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        UiSpacer(12.dp)

        TokenAndChainLogo(tokenLogo = tokenLogo, tokenTicker = ticker, chainLogo = chainLogo)

        UiSpacer(12.dp)

        val text = buildAnnotatedString {
            if (value.isNotEmpty()) {
                append(value)
                append(" ")
            }
            withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) { append(ticker) }
        }

        Text(
            text = text,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        if (fiatValue != null) {
            Text(
                text = fiatValue,
                style = Theme.brockmann.supplementary.captionSmall,
                color = Theme.v2.colors.text.tertiary,
            )
        }

        if (scope != null) {
            UiSpacer(4.dp)

            Text(
                text = scope,
                style = Theme.brockmann.supplementary.captionSmall,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun TokenAndChainLogo(
    tokenLogo: ImageModel,
    tokenTicker: String,
    chainLogo: Int?,
    tokenLogoSize: Dp = 36.dp,
    chainLogoSize: Dp = 20.dp,
    chainLogoOffset: DpOffset = DpOffset(x = 5.dp, y = 5.dp),
    tokenBorderColor: Color = Theme.v2.colors.border.light,
    chainBorderColor: Color = Theme.v2.colors.backgrounds.primary,
    tokenBackgroundErrorColor: Color = Theme.v2.colors.neutrals.n200,
    chainBackgroundColor: Color = Theme.v2.colors.neutrals.n100,
) {
    Box {
        TokenLogo(
            logo = tokenLogo,
            title = tokenTicker,
            modifier =
                Modifier.size(tokenLogoSize)
                    .border(width = 1.dp, color = tokenBorderColor, shape = CircleShape)
                    .align(Alignment.Center),
            errorLogoModifier =
                Modifier.size(tokenLogoSize).clip(CircleShape).background(tokenBackgroundErrorColor),
        )

        chainLogo?.let {
            Image(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier =
                    Modifier.offset(x = chainLogoOffset.x, y = chainLogoOffset.y)
                        .size(chainLogoSize)
                        .clip(CircleShape)
                        .background(chainBackgroundColor, CircleShape)
                        .border(width = 2.dp, color = chainBorderColor, shape = CircleShape)
                        .align(BottomEnd),
            )
        }
    }
}

@Preview
@Composable
private fun VsOverviewTokenPreview() {
    VsOverviewToken(
        header = "You will receive",
        valuedToken =
            ValuedToken(
                token =
                    Coin(
                        chain = Chain.Arbitrum,
                        ticker = "ARB",
                        logo = "https://example.com/eth_logo.png",
                        address = "0x0000000000000000000000000000000000000000",
                        decimal = 18,
                        hexPublicKey = "",
                        priceProviderID = "ethereum",
                        contractAddress = "",
                        isNativeToken = true,
                    ),
                value = "0.02500000",
                fiatValue = "$45.00",
            ),
        shape =
            RoundedWithCutoutShape(
                cutoutPosition = CutoutPosition.Start,
                cutoutOffsetX = (-4).dp,
                cutoutRadius = 18.dp,
            ),
        modifier = Modifier,
    )
}
