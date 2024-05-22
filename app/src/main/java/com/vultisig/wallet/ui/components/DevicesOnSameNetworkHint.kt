package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun DevicesOnSameNetworkHint(
    title: String,
) {
    Image(
        painter = painterResource(id = R.drawable.wifi),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )

    UiSpacer(size = 8.dp)

    Text(
        modifier = Modifier
            .padding(horizontal = 24.dp),
        text = title,
        color = Theme.colors.neutral0,
        textAlign = TextAlign.Center,
        style = Theme.menlo.body1,
    )
}