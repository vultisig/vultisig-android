package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.DefiPositionsUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BondedTabContent(
    bondToNodeOnClick: () -> Unit,
    state: DefiPositionsUiModel,
    onClickBond: (String) -> Unit,
    onClickUnbond: (String) -> Unit,
) {
    TotalBondWidget(
        onClickBondToNode = bondToNodeOnClick,
        totalBonded = state.bonded.totalBondedAmount,
        isLoading = state.isLoading,
    )

    state.bonded.nodes.forEachIndexed { index, node ->
        ActiveNodeRow(
            node = node,
            onClickBond = { onClickBond(node.address) },
            onClickUnbond = { onClickUnbond(node.address) }
        )
    }
}

@Composable
internal fun TotalBondWidget(
    onClickBondToNode: () -> Unit,
    totalBonded: String,
    isLoading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.colors.borders.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.rune),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = stringResource(R.string.total_bonded_rune),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.extraLight,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .size(width = 120.dp, height = 28.dp)
                    )
                } else {
                    Text(
                        text = totalBonded,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.colors.text.primary,
                    )
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.bond_to_node),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickBondToNode,
            state = VsButtonState.Enabled,
        )
    }
}

@Composable
internal fun ActiveNodeRow(
    node: BondedNodeUiModel,
    onClickBond: () -> Unit,
    onClickUnbond: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.colors.borders.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row {
            Text(
                text = stringResource(R.string.active_nodes),
                style = Theme.brockmann.button.medium,
                color = Theme.v2.colors.text.light,
            )

            UiSpacer(1f)

            UiIcon(
                drawableResId = R.drawable.ic_caret_down,
                size = 16.dp,
                tint = Theme.colors.text.light,
                modifier = Modifier.rotate(180f)
            )
        }

        UiSpacer(16.dp)

        Row {
            Text(
                text = stringResource(R.string.node_address) + ": ${node.address}",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(1f)

            val statusStyle = getStyleByNodeStatus(node.status)

            Text(
                text = statusStyle.second,
                style = Theme.brockmann.body.s.medium,
                color = statusStyle.first,
            )
        }

        UiSpacer(16.dp)

        Text(
            text = stringResource(R.string.bonded_label) + " ${node.bondedAmount}",
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(16.dp)

        Row {
            InfoItem(
                icon = R.drawable.ic_icon_percentage,
                label = stringResource(R.string.apy),
                value = null,
            )

            UiSpacer(1f)

            Text(
                text = node.apy,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.alerts.success,
            )
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoItem(
                icon = R.drawable.calendar_days,
                label = stringResource(R.string.next_churn),
                value = node.nextChurn,
            )

            InfoItem(
                icon = R.drawable.ic_cup,
                label = stringResource(R.string.next_award),
                value = node.nextAward
            )
        }

        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = stringResource(R.string.unbond),
                icon = R.drawable.ic_bond,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickUnbond,
                modifier = Modifier.weight(1f),
                enabled = node.status.canUnbond,
                iconCircleColor = Theme.v2.colors.text.extraLight
            )

            ActionButton(
                title = stringResource(R.string.bond),
                icon = R.drawable.ic_unbond,
                background = Theme.v2.colors.primary.accent3,
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickBond,
                modifier = Modifier.weight(1f),
                enabled = node.status.canBond,
                iconCircleColor = Theme.v2.colors.primary.accent4
            )
        }
    }
}

@Composable
fun InfoItem(icon: Int, label: String, value: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(
                size = 16.dp,
                drawableResId = icon,
                contentDescription = null,
                tint = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(4.dp)

            Text(
                text = label,
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )
        }

        if (value != null) {
            UiSpacer(6.dp)

            Text(
                text = value,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.light,
            )
        }
    }
}

@Composable
fun ActionButton(
    title: String,
    icon: Int,
    background: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    contentColor: Color,
    iconCircleColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
            disabledContainerColor = background.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = if (enabled) {
            border
        } else {
            border?.let {
                BorderStroke(
                    width = it.width,
                    color = when (val brush = it.brush) {
                        is SolidColor -> brush.value.copy(alpha = 0.5f)
                        else -> Color.Gray.copy(alpha = 0.5f) // fallback for gradient brushes
                    }
                )
            }
        },
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }

        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun getStyleByNodeStatus(nodeStatus: BondNodeState): Pair<Color, String> {
    val successColor = Theme.v2.colors.alerts.success
    val warningColor = Theme.v2.colors.alerts.warning

    return when (nodeStatus) {
        BondNodeState.WHITELISTED -> Pair(successColor, "Whitelisted")
        BondNodeState.STANDBY -> Pair(successColor, "StandBy")
        BondNodeState.READY -> Pair(successColor, "Ready")
        BondNodeState.ACTIVE -> Pair(successColor, "Active")
        BondNodeState.DISABLED -> Pair(warningColor, "Disabled")
        BondNodeState.UNKNOWN -> Pair(warningColor, "Unknown")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun BondedTabContentPreview() {
    BondedTabContent(
        state = DefiPositionsUiModel(),
        bondToNodeOnClick = { },
        onClickBond = {},
        onClickUnbond = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TotalBondWidgetPreview() {
    TotalBondWidget(
        onClickBondToNode = { },
        totalBonded = "50 RUNE"
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun NodeListPreview() {
    ActiveNodeRow(
        BondedNodeUiModel(
            address = "thor1abcd...xyz",
            status = BondNodeState.DISABLED,
            apy = "12.5%",
            bondedAmount = "1000 RUNE",
            nextAward = "20 RUNE",
            nextChurn = "Oct 15, 25"
        ),
        onClickBond = {},
        onClickUnbond = {},
    )
}