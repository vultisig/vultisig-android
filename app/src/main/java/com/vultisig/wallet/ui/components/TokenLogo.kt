package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier = modifier,
        error = {
            Box(
                modifier = errorLogoModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.first().toString(),
                    color = Theme.colors.oxfordBlue600Main,
                    style = Theme.montserrat.subtitle1
                )
            }
        }
    )
}