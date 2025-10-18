package com.vultisig.wallet.ui.screens.v2.customtoken.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton

@Composable
internal fun SearchTokenResult(
    onAddTokenClick: () -> Unit,
    token: Coin,
) {
    Column {
        SearchedTokenInfo(token)
        UiSpacer(
            size = 16.dp
        )
        VsButton(
            label = stringResource(
                R.string.custom_token_add_token,
                token.ticker
            ),
            onClick = onAddTokenClick,
            modifier = Modifier.Companion
                .fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun SearchTokenResultPreview() {
    SearchTokenResult(
        onAddTokenClick = {},
        token = Coins.Ethereum.GRT,
    )
}