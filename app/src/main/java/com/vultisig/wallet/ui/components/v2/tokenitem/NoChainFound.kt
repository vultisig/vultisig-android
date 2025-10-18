package com.vultisig.wallet.ui.components.v2.tokenitem

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.theme.Theme

@Preview
@Composable
internal fun NoChainFound() {
    TopShineContainer {
        Column(
            horizontalAlignment = Alignment.Companion.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.iconcrypto),
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(20.dp),
            )
            UiSpacer(14.dp)
            Text(
                text = stringResource(R.string.chain_selection_no_chains_found),
                style = Theme.brockmann.headings.subtitle,
                color = Theme.colors.text.primary,
            )
        }
    }
}