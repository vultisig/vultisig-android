package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.BondedTabUiModel
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BondedTabContent(
    bondToNodeOnClick: () -> Unit,
    state: ThorchainDefiPositionsUiModel,
    onClickBond: (String) -> Unit,
    onClickUnbond: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderDeFiWidget(
            title = stringResource(R.string.total_bonded_rune),
            iconRes = R.drawable.rune,
            buttonText = stringResource(R.string.bond_to_node),
            onClickAction = bondToNodeOnClick,
            totalAmount = state.bonded.totalBondedAmount,
            totalPrice = state.bonded.totalBondedPrice,
            isLoading = state.bonded.isLoading,
            isBalanceVisible = state.isBalanceVisible,
        )

        if (state.bonded.nodes.isNotEmpty()) {
            ActiveNodesWidget(
                nodes = state.bonded.nodes,
                onClickBond = onClickBond,
                onClickUnbond = onClickUnbond,
                isBalanceVisible = state.isBalanceVisible,
            )
        }
    }
}

@Composable
internal fun ActiveNodesWidget(
    nodes: List<BondedNodeUiModel>,
    onClickBond: (String) -> Unit,
    onClickUnbond: (String) -> Unit,
    isBalanceVisible: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.v2.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row {
            Text(
                text = stringResource(R.string.active_nodes),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.secondary,
            )

            UiSpacer(1f)

            UiIcon(
                drawableResId = R.drawable.ic_caret_down,
                size = 16.dp,
                tint = Theme.v2.colors.text.secondary,
                modifier = Modifier.rotate(180f)
            )
        }

        nodes.forEachIndexed { index, node ->
            if (index == 0) {
                UiSpacer(16.dp)
            } else {
                UiSpacer(16.dp)

                UiHorizontalDivider()

                UiSpacer(16.dp)
            }

            NodeContent(
                node = node,
                onClickBond = { onClickBond(node.fullAddress) },
                onClickUnbond = { onClickUnbond(node.fullAddress) },
                isBalanceVisible = isBalanceVisible,
            )
        }
    }
}

@Composable
private fun NodeContent(
    node: BondedNodeUiModel,
    onClickBond: () -> Unit,
    onClickUnbond: () -> Unit,
    isBalanceVisible: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.node_address_formatted, node.address),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            UiSpacer(8.dp)

            val statusStyle = getStyleByNodeStatus(node.status)

            Text(
                text = statusStyle.second,
                style = Theme.brockmann.body.s.medium,
                color = statusStyle.first,
                modifier = Modifier.wrapContentWidth()
            )
        }

        UiSpacer(16.dp)

        Text(
            text = stringResource(R.string.bonded_amount, if (isBalanceVisible) node.bondedAmount else HIDE_BALANCE_CHARS),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(16.dp)

        ApyInfoItem(
            apy = node.apy
        )

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                InfoItem(
                    icon = R.drawable.calendar_days,
                    label = stringResource(R.string.next_churn),
                    value = node.nextChurn,
                )
            }

            Box(
                modifier = Modifier.weight(1f)
            ) {
                InfoItem(
                    icon = R.drawable.ic_cup,
                    label = stringResource(R.string.next_award),
                    value = if (isBalanceVisible) node.nextAward else HIDE_BALANCE_CHARS
                )
            }
        }

        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = stringResource(R.string.unbond),
                icon = R.drawable.ic_unbond,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickUnbond,
                modifier = Modifier.weight(1f),
                enabled = node.status.canUnbond,
                iconCircleColor = Theme.v2.colors.text.tertiary
            )

            ActionButton(
                title = stringResource(R.string.bond),
                icon = R.drawable.ic_bond,
                background = Theme.v2.colors.primary.accent3,
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickBond,
                modifier = Modifier.weight(1f),
                enabled = node.status.canBond,
                iconCircleColor = Theme.v2.colors.primary.accent4
            )
        }

        if (!node.status.canUnbond) {
            UiSpacer(16.dp)

            Text(
                text = stringResource(R.string.wait_until_node_churned_out),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun getStyleByNodeStatus(nodeStatus: BondNodeState): Pair<Color, String> {
    val successColor = Theme.v2.colors.alerts.success
    val warningColor = Theme.v2.colors.alerts.warning

    return when (nodeStatus) {
        BondNodeState.WHITELISTED -> Pair(successColor, stringResource(R.string.bond_node_state_whitelisted))
        BondNodeState.STANDBY -> Pair(successColor, stringResource(R.string.bond_node_state_standby))
        BondNodeState.READY -> Pair(successColor, stringResource(R.string.bond_node_state_ready))
        BondNodeState.ACTIVE -> Pair(successColor, stringResource(R.string.bond_node_state_active))
        BondNodeState.DISABLED -> Pair(warningColor, stringResource(R.string.bond_node_state_disabled))
        BondNodeState.UNKNOWN -> Pair(warningColor, stringResource(R.string.bond_node_state_unknown))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Bonded Tab - With Nodes")
@Composable
private fun BondedTabContentPreview() {
    val mockNodes = listOf(
        BondedNodeUiModel(
            address = "thor1abcd...xyz",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.ACTIVE,
            apy = "12.5%",
            bondedAmount = "1000 RUNE",
            nextAward = "20 RUNE",
            nextChurn = "Oct 15, 25"
        ),
        BondedNodeUiModel(
            address = "thor1efgh...123",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.STANDBY,
            apy = "11.2%",
            bondedAmount = "500 RUNE",
            nextAward = "10 RUNE",
            nextChurn = "Oct 16, 25"
        ),
        BondedNodeUiModel(
            address = "thor1whit...789",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.WHITELISTED,
            apy = "0%",
            bondedAmount = "100 RUNE",
            nextAward = "0 RUNE",
            nextChurn = "N/A"
        ),
        BondedNodeUiModel(
            address = "thor1ready...abc",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.READY,
            apy = "10.8%",
            bondedAmount = "750 RUNE",
            nextAward = "15 RUNE",
            nextChurn = "Oct 17, 25"
        ),
        BondedNodeUiModel(
            address = "thor1dis...def",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.DISABLED,
            apy = "0%",
            bondedAmount = "250 RUNE",
            nextAward = "0 RUNE",
            nextChurn = "N/A"
        )
    )
    
    BondedTabContent(
        state = ThorchainDefiPositionsUiModel(
            bonded = BondedTabUiModel(
                totalBondedAmount = "2600 RUNE",
                nodes = mockNodes
            )
        ),
        bondToNodeOnClick = { },
        onClickBond = {},
        onClickUnbond = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Bonded Tab - Loading")
@Composable
private fun BondedTabContentLoadingPreview() {
    BondedTabContent(
        state = ThorchainDefiPositionsUiModel(
            bonded = BondedTabUiModel(
                isLoading = true,
                totalBondedAmount = "0 RUNE",
                nodes = emptyList()
            )
        ),
        bondToNodeOnClick = { },
        onClickBond = {},
        onClickUnbond = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Bonded Tab - Empty")
@Composable
private fun BondedTabContentEmptyPreview() {
    BondedTabContent(
        state = ThorchainDefiPositionsUiModel(
            bonded = BondedTabUiModel(
                isLoading = false,
                totalBondedAmount = "0 RUNE",
                nodes = emptyList()
            )
        ),
        bondToNodeOnClick = { },
        onClickBond = {},
        onClickUnbond = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Total Bond Widget")
@Composable
private fun HeaderDeFiWidgetPreview() {
    HeaderDeFiWidget(
        onClickAction = { },
        totalAmount = "2600 RUNE",
        iconRes = R.drawable.rune,
        buttonText = "Bond to Node",
        title = "Total Bonded Rune",
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Active Nodes - Multiple States")
@Composable
private fun ActiveNodesWidgetPreview() {
    val mockNodes = listOf(
        BondedNodeUiModel(
            address = "thor1abcd...xyz",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.ACTIVE,
            apy = "12.5%",
            bondedAmount = "1000 RUNE",
            nextAward = "20 RUNE",
            nextChurn = "Oct 15, 25"
        ),
        BondedNodeUiModel(
            address = "thor1efgh...123",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.DISABLED,
            apy = "11.2%",
            bondedAmount = "500 RUNE",
            nextAward = "10 RUNE",
            nextChurn = "Oct 16, 25"
        ),
        BondedNodeUiModel(
            address = "thor1stand...456",
            fullAddress = "thor1abcd...xyz",
            status = BondNodeState.STANDBY,
            apy = "10.8%",
            bondedAmount = "750 RUNE",
            nextAward = "15 RUNE",
            nextChurn = "Oct 17, 25"
        )
    )
    
    ActiveNodesWidget(
        nodes = mockNodes,
        onClickBond = {},
        onClickUnbond = {},
    )
}


private val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()
