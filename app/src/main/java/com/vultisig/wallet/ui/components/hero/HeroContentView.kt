package com.vultisig.wallet.ui.components.hero

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

/**
 * Renders a [HeroContent] inside the verify / done card.
 *
 * The composable owns no padding or background — the parent card provides those — so it can be
 * dropped into the existing `tokenContent` slot of `TxDoneScaffold` and the inner column of
 * `VerifySendScreen` without altering surrounding layout.
 */
@Composable
internal fun HeroContentView(content: HeroContent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (content) {
            is HeroContent.Title -> TitleOnlyHero(text = content.text, caption = null)
            HeroContent.Unverified ->
                TitleOnlyHero(
                    text = stringResource(R.string.dapp_hero_unverified_function_title),
                    caption = stringResource(R.string.dapp_hero_unverified_function_subtitle),
                )
            is HeroContent.Send -> SendHero(content)
            is HeroContent.Swap -> SwapHero(content)
        }
    }
}

/**
 * Renders a centered title-only hero with an optional explainer caption.
 *
 * Two callers:
 * - [HeroContent.Title] passes a bare function name and no caption — used as the loading-tick
 *   fallback on done screens before simulation propagates.
 * - [HeroContent.Unverified] passes the localized "Unverified function" + "Review the details below
 *   before signing" pair — emitted by the use case when Blockaid simulation has loaded but returned
 *   no balance change.
 *
 * Hierarchy: warning glyph → title at `title2` weight → explainer at `body.s.medium`. The glyph is
 * always rendered because both states represent a missing balance change and should visually flag
 * caution.
 */
@Composable
private fun TitleOnlyHero(text: String, caption: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_triangle_alert,
            size = 24.dp,
            tint = Theme.v2.colors.alerts.warning,
        )
        Text(
            text = text,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        caption?.let {
            Text(
                text = it,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SendHero(content: HeroContent.Send) {
    content.title?.let { TitleAbove(it) }
    HeroCoinRow(coin = content.coin, iconSize = 36.dp)
}

@Composable
private fun SwapHero(content: HeroContent.Swap) {
    content.title?.let { TitleAbove(it) }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HeroCoinRow(coin = content.from, iconSize = 28.dp)
        ArrowDivider()
        HeroCoinRow(coin = content.to, iconSize = 28.dp)
    }
}

@Composable
private fun TitleAbove(title: String) {
    Text(
        text = title,
        style = Theme.brockmann.body.s.medium,
        color = Theme.v2.colors.text.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HeroCoinRow(coin: HeroCoinAmount, iconSize: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (coin.logo.isNotEmpty()) {
            TokenLogo(
                logo = coin.logo,
                title = coin.ticker,
                // Border BEFORE clip so the stroke is fully painted before
                // the circle mask cuts the bounds — reversing the order can
                // shave a pixel off the outer edge on some densities.
                modifier =
                    Modifier.size(iconSize)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = CircleShape,
                        )
                        .clip(CircleShape),
                errorLogoModifier =
                    Modifier.size(iconSize)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.neutrals.n200),
            )
        } else {
            // Native asset fallback: solid circle with the ticker initials. The
            // hero never blocks for an image — empty logo means "Blockaid did
            // not give us a stable URL", so we surface text instead of spinning.
            Box(
                modifier =
                    Modifier.size(iconSize)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.neutrals.n200),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = coin.ticker.take(3).uppercase(),
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }
        val text = buildAnnotatedString {
            append(coin.amount)
            withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) {
                append(" ${coin.ticker}")
            }
        }
        Text(
            text = text,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ArrowDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Theme.v2.colors.border.light))
        UiIcon(
            drawableResId = R.drawable.ic_arrow_down,
            size = 12.dp,
            tint = Theme.v2.colors.text.tertiary,
        )
        // Use the localized resource as-is; lowercasing translated strings
        // mid-sentence breaks grammar in some locales (e.g. Russian, German).
        Text(
            text = stringResource(id = R.string.swap_form_dst_token_title),
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.v2.colors.text.tertiary,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Theme.v2.colors.border.light))
    }
}

// ---------- Previews ---------------------------------------------------------

@Preview
@Composable
private fun PreviewHeroContentTitleOnly() {
    HeroContentView(content = HeroContent.Title(text = "Approve"))
}

@Preview
@Composable
private fun PreviewHeroContentUnverified() {
    HeroContentView(content = HeroContent.Unverified)
}

@Preview
@Composable
private fun PreviewHeroContentSend() {
    HeroContentView(
        content =
            HeroContent.Send(
                title = "Approve",
                coin = HeroCoinAmount(amount = "100", ticker = "USDC", logo = ""),
            )
    )
}

@Preview
@Composable
private fun PreviewHeroContentSwap() {
    HeroContentView(
        content =
            HeroContent.Swap(
                title = "Swap",
                from = HeroCoinAmount(amount = "1.0", ticker = "ETH", logo = ""),
                to = HeroCoinAmount(amount = "3,150.42", ticker = "USDC", logo = ""),
            )
    )
}
