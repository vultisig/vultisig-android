package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenFiatToggle(
    isTokenSelected: Boolean,
    onTokenSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(IntrinsicSize.Min)
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = CircleShape)
    ) {
        LookaheadScope {
            Box(
                Modifier.animatePlacementInScope(lookaheadScope = this@LookaheadScope)
                    .padding(all = 4.dp)
                    .background(color = Theme.v2.colors.primary.accent3, shape = CircleShape)
                    .padding(all = 8.dp)
                    .size(16.dp)
                    .align(if (isTokenSelected) Alignment.TopCenter else Alignment.BottomCenter)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(all = 4.dp),
            ) {
                TokenFiatToggleButton(
                    drawableResId = R.drawable.ic_coins,
                    onClick = { onTokenSelected(true) },
                )

                TokenFiatToggleButton(
                    drawableResId = R.drawable.ic_dollar_sign,
                    onClick = { onTokenSelected(false) },
                )
            }
        }
    }
}

@Composable
private fun TokenFiatToggleButton(@DrawableRes drawableResId: Int, onClick: () -> Unit) {
    UiIcon(
        drawableResId = drawableResId,
        size = 16.dp,
        tint = Theme.v2.colors.text.secondary,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(all = 8.dp),
    )
}
