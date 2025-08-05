package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ChainSelectionScreen(
    onSelectChain: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiGradientDivider(
                initialColor = Theme.colors.backgrounds.secondary,
                endColor = Theme.colors.backgrounds.secondary,
            )

            Text(
                text = "Select Chain",
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.m.medium,
                modifier = Modifier.padding(vertical = 16.dp)
            )


            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ChainItem(
                        iconRes = R.drawable.ton,
                        name = "Ton",
                        isSelected = false,
                        onClick = {}
                    )
                }
                item {
                    ChainItem(
                        iconRes = R.drawable.ethereum,
                        name = "Ethereum",
                        isSelected = true,
                        onClick = {},
                    )
                }
                item {
                    ChainItem(
                        iconRes = R.drawable.bitcoin,
                        name = "Bitcoin",
                        isSelected = false,
                        onClick = {},
                    )
                }
                item {
                    ChainItem(
                        iconRes = R.drawable.bitcoincash,
                        name = "Bitcoin Cash",
                        isSelected = false,
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Composable
fun ChainItem(
    iconRes: Int,
    name: String,
    isSelected: Boolean,
    onClick: (String) -> Unit,
) {
    val borderColor = if (isSelected) {
        Brush.horizontalGradient(
            listOf(Color(0xFF3333F2), Color(0xFF6A97E4))
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Row(
        modifier = Modifier
            .wrapContentSize()
            .height(50.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Theme.colors.backgrounds.secondary)
            .border(2.dp, borderColor, RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "$name logo",
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = name,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
        )
    }
}