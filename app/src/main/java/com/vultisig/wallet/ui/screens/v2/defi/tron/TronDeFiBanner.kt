package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.TronRed

private val TronBannerGradientTop = TronRed.copy(alpha = 0.09f)
private val TronBannerGradientBottom = TronRed.copy(alpha = 0f)
private val TronBannerBorder = TronRed.copy(alpha = 0.17f)

@Composable
internal fun TronDeFiBanner(isLoading: Boolean, totalValue: String, isBalanceVisible: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(TronBannerGradientTop, TronBannerGradientBottom)
                    )
                )
                .border(1.dp, TronBannerBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.fillMaxHeight()
                    .width(200.dp)
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.tron),
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(6.dp)

            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 150.dp, height = 32.dp))
            } else {
                Text(
                    text = if (isBalanceVisible) totalValue else HIDE_BALANCE_CHARS,
                    style = Theme.satoshi.price.title1,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }
        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
            Image(
                painter = painterResource(R.drawable.tron_banner),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}
