package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenLogo(
    modifier: Modifier,
    errorLogoModifier: Modifier,
    logo: ImageModel,
    title: String
) {
    SubcomposeAsyncImage(
        model = logo,
        contentDescription = null,
         modifier = modifier
             .clip(CircleShape)
             .background(Theme.colors.neutral100),
        error = {
            Box(
                modifier = errorLogoModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (title.isNotEmpty()) title.first().toString() else "",
                    color = Theme.colors.oxfordBlue600Main,
                    style = Theme.montserrat.subtitle1
                )
            }
        }
    )
}