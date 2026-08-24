package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.TokenInfoUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Local facts about the asset — price, network, contract, decimals, explorer. None of this needs a
 * network call, so the section renders for every coin including the pool-priced ones that get no
 * chart.
 *
 * [price] is passed only when the chart is absent: with a chart on screen the price already
 * headlines it, and repeating it four rows down is noise. Without one this is the only place the
 * price appears.
 */
@Composable
internal fun TokenInfoSection(
    info: TokenInfoUiModel,
    onExplorer: () -> Unit,
    modifier: Modifier = Modifier,
    price: String? = null,
) {
    val rows: List<@Composable () -> Unit> = buildList {
        if (price != null) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.token_details_bottom_sheet_price),
                    value = price,
                )
            }
        }
        add {
            TokenDetailRow(
                label = stringResource(R.string.token_details_bottom_sheet_network),
                value = info.network,
            )
        }
        if (info.contractAddress != null) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.token_details_contract_address),
                    trailing = { ContractValue(address = info.contractAddress) },
                )
            }
        }
        if (info.decimals != null) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.token_details_decimals),
                    value = info.decimals,
                )
            }
        }
        if (info.hasExplorerLink) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.view_on_explorer),
                    onClick = onExplorer,
                    trailing = {
                        Image(
                            painter = painterResource(R.drawable.ic_square_arrow_top_right),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Theme.v2.colors.text.tertiary),
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
    }

    TokenDetailSection(
        title = stringResource(R.string.token_details_token_info_title),
        rows = rows,
        modifier = modifier,
    )
}

/** Middle-ellipsized so a 42-character contract stays one line, with a copy affordance. */
@Composable
private fun ContractValue(address: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = address,
            style = Theme.satoshi.price.bodyS,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        UiSpacer(size = 6.dp)
        CopyIcon(textToCopy = address, size = 14.dp, tint = Theme.v2.colors.text.tertiary)
    }
}

@Preview
@Composable
private fun TokenInfoSectionPreview() {
    TokenInfoSection(
        info =
            TokenInfoUiModel(
                network = "Ethereum",
                contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                decimals = "18",
                hasExplorerLink = true,
            ),
        onExplorer = {},
        price = "$3,010.77",
    )
}
