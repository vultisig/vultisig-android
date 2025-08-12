package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.screens.select.NetworkUiModel
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ChainSelectionScreen(
    onSelectChain: (Chain) -> Unit = {},
    chains: List<NetworkUiModel>,
    selectedChain: Chain,
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
                initialColor = Theme.colors.backgrounds.primary,
                endColor = Theme.colors.backgrounds.primary,
            )

            Text(
                text = stringResource(R.string.select_chain_title),
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.m.medium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(chains) { network ->
                    ChainItem(
                        chain = network.chain,
                        logo = network.logo,
                        isSelected = network.chain.id == selectedChain.id,
                        onClick = { onSelectChain(it) },
                    )
                }
            }
        }
    }
}

@Composable
fun ChainItem(
    chain: Chain,
    logo: ImageModel,
    isSelected: Boolean,
    onClick: (Chain) -> Unit,
) {
    val borderColor = if (isSelected) {
        Brush.horizontalGradient(
            listOf(Colors.Default.persianBlue200,Colors.Default.persianBlue400)
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
            .padding(horizontal = 16.dp)
            .clickable { onClick(chain) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TokenLogo(
            errorLogoModifier = Modifier
                .size(32.dp)
                .background(Theme.colors.neutral100),
            logo = logo,
            title = "${chain.name} logo",
            modifier = Modifier
                .size(26.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = chain.name,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
        )
    }
}