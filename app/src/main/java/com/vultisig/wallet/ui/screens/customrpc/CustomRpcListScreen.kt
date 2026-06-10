package com.vultisig.wallet.ui.screens.customrpc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.customrpc.CustomRpcListUiState
import com.vultisig.wallet.ui.models.customrpc.CustomRpcListViewModel
import com.vultisig.wallet.ui.models.customrpc.CustomRpcRowUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CustomRpcListScreen() {
    val viewModel = hiltViewModel<CustomRpcListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    CustomRpcListScreen(
        state = state,
        onRowClick = viewModel::onRowClick,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun CustomRpcListScreen(
    state: CustomRpcListUiState,
    onRowClick: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(R.string.custom_rpc_title), onBackClick = onBackClick) {
        Column {
            Text(
                text = stringResource(R.string.custom_rpc_list_subtitle),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
            UiSpacer(size = 12.dp)
            LazyColumn(
                modifier =
                    Modifier.clip(RoundedCornerShape(size = 12.dp))
                        .background(color = Theme.v2.colors.backgrounds.surface1)
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.chainId }) { index, row ->
                    CustomRpcRow(
                        row = row,
                        onClick = { onRowClick(row.chainId) },
                        isLastItem = index == state.rows.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRpcRow(row: CustomRpcRowUiModel, onClick: () -> Unit, isLastItem: Boolean) {
    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = row.logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(99.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.chainName,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
                if (row.customUrl != null) {
                    UiSpacer(size = 2.dp)
                    Text(
                        text = row.customUrl,
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CustomRpcStatusChip(isCustom = row.isCustom)
        }
        if (!isLastItem) {
            HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)
        }
    }
}

@Composable
private fun CustomRpcStatusChip(isCustom: Boolean) {
    val label =
        if (isCustom) stringResource(R.string.custom_rpc_chip_custom)
        else stringResource(R.string.custom_rpc_chip_default)
    val color = if (isCustom) Theme.v2.colors.alerts.success else Theme.v2.colors.text.tertiary
    Text(
        text = label,
        style = Theme.brockmann.supplementary.caption,
        color = color,
        modifier =
            Modifier.background(
                    color = Theme.v2.colors.backgrounds.surface2,
                    shape = RoundedCornerShape(size = 99.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview
@Composable
private fun CustomRpcListScreenPreview() {
    CustomRpcListScreen(
        state =
            CustomRpcListUiState(
                rows =
                    listOf(
                        CustomRpcRowUiModel(
                            chainId = "Ethereum",
                            chainName = "Ethereum",
                            logo = R.drawable.ethereum,
                            customUrl = "https://my-node.example/eth",
                        ),
                        CustomRpcRowUiModel(
                            chainId = "Cosmos",
                            chainName = "Cosmos",
                            logo = R.drawable.atom,
                            customUrl = null,
                        ),
                    )
            ),
        onRowClick = {},
        onBackClick = {},
    )
}
