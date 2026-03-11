package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.theme.Theme

val BOTH_CRYPTO_CONNECTION_TYPES = listOf(CryptoConnectionType.Wallet, CryptoConnectionType.Defi)

val ALL_CRYPTO_CONNECTION_TYPES =
    listOf(CryptoConnectionType.Wallet, CryptoConnectionType.Defi, CryptoConnectionType.Agent)

val ONLY_WALLET = listOf(CryptoConnectionType.Wallet)

val ONLY_DEFI = listOf(CryptoConnectionType.Defi)

@Composable
internal fun CryptoConnectionSelect(
    modifier: Modifier = Modifier,
    activeType: CryptoConnectionType,
    availableCryptoTypes: List<CryptoConnectionType> = ALL_CRYPTO_CONNECTION_TYPES,
    onTypeClick: (CryptoConnectionType) -> Unit,
) {
    val selectedIndex = availableCryptoTypes.indexOf(activeType).coerceAtLeast(0)
    val count = availableCryptoTypes.size

    LookaheadScope {
        val c1 = Color(0xFF284570)
        val c2 = Theme.v2.colors.backgrounds.secondary
        Box(
            modifier =
                Modifier.clip(CircleShape)
                    .background(
                        brush = Brush.sweepGradient(colors = listOf(c2, c1, c1, c1, c2, c2))
                    )
                    .padding(1.dp)
        ) {
            Box(
                modifier =
                    modifier
                        .height(64.dp)
                        .width(92.dp * count)
                        .clip(CircleShape)
                        .background(Color(0xFF0d2446))
                        .padding(all = 4.dp)
            ) {
                val alignment =
                    when {
                        count <= 1 -> Alignment.CenterStart
                        else -> {
                            val bias =
                                if (count == 1) 0f else (2f * selectedIndex / (count - 1)) - 1f
                            BiasAlignment(horizontalBias = bias, verticalBias = 0f)
                        }
                    }

                Box(
                    modifier =
                        Modifier.animatePlacementInScope(this@LookaheadScope)
                            .clip(CircleShape)
                            .background(Color(0xFF1e3250))
                            .shadow(
                                elevation = 1.dp,
                                shape = CircleShape,
                                spotColor = Theme.v2.colors.neutrals.n100.copy(alpha = 0.2f),
                                clip = true,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(1f / count)
                            .align(alignment)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    availableCryptoTypes.forEach { type ->
                        val (text, icon) =
                            when (type) {
                                CryptoConnectionType.Wallet ->
                                    stringResource(R.string.wallet) to R.drawable.wallet
                                CryptoConnectionType.Defi ->
                                    stringResource(R.string.defi) to R.drawable.coins_add
                                CryptoConnectionType.Agent ->
                                    stringResource(R.string.agent_title) to R.drawable.sparkles
                            }

                        WalletEarnOption(
                            modifier = Modifier.weight(1f),
                            onClick = { onTypeClick(type) },
                            text = text,
                            icon = icon,
                            enabled = activeType == type,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletEarnOption(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isClickable: Boolean = true,
    text: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                enabled = isClickable,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val contentColor =
            if (enabled) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary
        val contentColorAnimated by animateColorAsState(contentColor)
        UiIcon(drawableResId = icon, size = 24.dp, tint = contentColorAnimated)
        Text(
            text = text,
            style = Theme.brockmann.supplementary.caption,
            color = contentColorAnimated,
        )
    }
}

@Preview
@Composable
private fun PreviewWalletEarnSelect() {
    CryptoConnectionSelect(
        activeType = CryptoConnectionType.Defi,
        onTypeClick = {},
        availableCryptoTypes = ALL_CRYPTO_CONNECTION_TYPES,
    )
}
