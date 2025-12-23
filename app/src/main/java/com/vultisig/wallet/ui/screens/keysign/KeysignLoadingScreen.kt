package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeysignLoadingScreen(
    text: String,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = Theme.colors.neutrals.n50,
            style = Theme.menlo.subtitle1
        )

        UiSpacer(size = 32.dp)

        UiCirclesLoader()
    }
}

@Preview
@Composable
private fun KeysignLoadingScreenPreview() {
    KeysignLoadingScreen(
        text = stringResource(R.string.join_keysign_discovering_session_id),
    )
}