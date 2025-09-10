package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun WalletEarnSelect(
    modifier: Modifier = Modifier
) {

    var x by remember {
        mutableStateOf(false)
    }

    LookaheadScope {

        Box(
            modifier = modifier
                .border(
                    width = 0.1.dp,
                    color = Color.White,
                    shape = CircleShape
                )
                .clip(
                    CircleShape
                )
                .background(Theme.v2.colors.backgrounds.tertiary)
                .padding(
                    all = 4.dp,
                )
                .height(
                    IntrinsicSize.Min
                )
                .width(100.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = if (x)
                    Alignment.TopEnd else Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .animatePlacementInScope(this@LookaheadScope)
                        .clip(CircleShape)
                        .background(Theme.colors.text.button.dark)
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A",
                    modifier = Modifier.clickOnce(
                        onClick = {
                            x = true
                        }
                    )
                )
                UiSpacer(16.dp)
                Text(
                    text = "B",
                    modifier = Modifier.clickOnce(
                        onClick = {
                            x = false
                        }
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewWalletEarnSelect() {
    WalletEarnSelect()
}

