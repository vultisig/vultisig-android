package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainLogo(name: String, logo: ImageModel?) {
    SubcomposeAsyncImage(
        model = logo,
        contentDescription = null,
        modifier = Modifier.size(24.dp).clip(Theme.v2.radius.sm),
        error = {
            Box(
                modifier =
                    Modifier.size(24.dp)
                        .clip(Theme.v2.radius.sm)
                        .background(color = Theme.v2.colors.backgrounds.tertiary_2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.toString() ?: "",
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.supplementary.caption,
                )
            }
        },
    )
}
