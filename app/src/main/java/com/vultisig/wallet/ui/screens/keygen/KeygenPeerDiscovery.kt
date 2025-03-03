package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun FastPeerDiscovery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = CenterHorizontally,
    ) {
        UiSpacer(size = 74.dp)

        Text(
            text = stringResource(R.string.keygen_vultiserver_peer_discovery_waiting),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        )

        UiSpacer(size = 48.dp)

        UiCirclesLoader()
        UiSpacer(size = 48.dp)
        Text(
            text = stringResource(R.string.keygen_vultiserver_peer_discovery_please_wait),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.body2
        )
    }
}