package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainLogo(uiModel: ChainTokensUiModel) {
    SubcomposeAsyncImage(
        model = uiModel.chainLogo,
        contentDescription = null,
        modifier = Modifier
            .size(24.dp)
            .clip(
                RoundedCornerShape(
                    size = 8.dp
                )
            ),

        error = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(
                        RoundedCornerShape(
                            size = 8.dp
                        )
                    )
                    .background(
                        color = Theme.colors.backgrounds.tertiary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiModel.chainName.firstOrNull()?.toString() ?: "",
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.supplementary.caption
                )
            }
        }
    )
}