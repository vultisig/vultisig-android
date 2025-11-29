package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenLogo(
    modifier: Modifier,
    errorLogoModifier: Modifier,
    logo: ImageModel,
    title: String
) {
    val context = LocalContext.current

    val imageRequest = if (logo is String && logo.contains("ipfs.io", ignoreCase = true)) {
        ImageRequest.Builder(context)
            .data(logo)
            .addHeader(USER_AGENT, DEFAULT_USER_AGENT)
            .build()
    } else {
        logo
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = null,
         modifier = modifier
             .clip(CircleShape)
             .background(Theme.v2.colors.backgrounds.transparent, CircleShape),
        error = {
            Box(
                modifier = errorLogoModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (title.isNotEmpty()) title.first().toString() else "",
                    color = Theme.v2.colors.backgrounds.secondary,
                    style = Theme.montserrat.subtitle1
                )
            }
        }
    )
}

private const val USER_AGENT = "User-Agent"
private const val DEFAULT_USER_AGENT = "Vultisig/1.0 (Android 14; Pixel 7 Pro)"