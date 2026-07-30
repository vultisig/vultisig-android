package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenMetaAddressRow
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenMetaRow
import com.vultisig.wallet.ui.models.TokenInfoUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Contract address and decimals — always available from the already-loaded coin, independent of
 * whether a CoinGecko chart/stats source exists. Network/explorer are shown elsewhere on this
 * screen already. Contract address is absent on most non-EVM chains (Cosmos-SDK chains, etc.) that
 * have no per-token contract concept — the row is simply omitted for those, same as any other null
 * field here.
 */
@Composable
internal fun TokenInfoSection(info: TokenInfoUiModel, modifier: Modifier = Modifier) {
    val contractAddress = info.contractAddress
    val decimals = info.decimals
    if (contractAddress == null && decimals == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.token_details_token_info_title),
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
        )
        UiSpacer(size = 12.dp)
        TopShineContainer(backgroundColor = Theme.v2.colors.backgrounds.surface1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (contractAddress != null) {
                    TokenMetaAddressRow(
                        key = stringResource(R.string.token_details_contract_address),
                        address = contractAddress,
                    )
                    if (decimals != null) {
                        UiHorizontalDivider(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (decimals != null) {
                    TokenMetaRow(
                        key = stringResource(R.string.token_details_decimals),
                        value = decimals,
                    )
                }
            }
        }
    }
}
