package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NotEnabledContainer(
    title: String,
    content: String,
    action: @Composable (() -> Unit)? = null,
) {
    TopShineContainer {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.iconcrypto),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp),
            )
            UiSpacer(12.dp)

            Text(
                text = title,
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(8.dp)

            Text(
                text = content,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.extraLight,
                textAlign = TextAlign.Center
            )

            if (action != null) {
                UiSpacer(16.dp)

                action()
            }
        }
    }
}