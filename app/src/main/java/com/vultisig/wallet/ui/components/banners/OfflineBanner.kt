package com.vultisig.wallet.ui.components.banners

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun OfflineBanner(isOffline: Boolean) {
    AnimatedVisibility(
        visible = isOffline,
        label = "connection state banner",
        modifier = Modifier.Companion,
        enter = slideInVertically(),
        exit = slideOutVertically(),
        content = {
            Text(
                text = stringResource(R.string.offline),
                style = Theme.menlo.heading5,
                textAlign = TextAlign.Companion.Center,
                color = Theme.v2.colors.backgrounds.primary,
                fontSize = 12.sp,
                modifier = Modifier.Companion
                    .background(Theme.v2.colors.backgrounds.amber)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    )
}