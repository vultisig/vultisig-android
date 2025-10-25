package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun BondedTabContent(bondToNodeOnClick: () -> Unit) {
    TotalBondWidget(
        onClickBondToNode = bondToNodeOnClick,
    )
}

@Composable
fun TotalBondWidget(
    onClickBondToNode: () -> Unit,
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

                Text(
                    text = "0 RUNE",
                    style = Theme.brockmann.headings.title1,
                    color = Theme.colors.text.primary,
                )
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
internal fun NodeList(nodes: List<BondedNodeUiModel>) {
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
        Text(
            text = "Active Nodes",
            style = Theme.brockmann.button.medium,
            color = Theme.v2.colors.text.light,
        )

        UiSpacer(16.dp)

        Row {
            Text(
                text = "Node Address:",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(1f)

            Text(
                text = "Churned Out",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.alerts.warning,
            )
        }

        UiSpacer(16.dp)

        Text(
            text = "Bonded 800 RUNE",
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(16.dp)

        Row {
            Text(
                text = "APY",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(1f)

            Text(
                text = "0%",
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
                label = "Next Churn",
                value = "Oct 15, 25",
            )

            InfoItem(
                icon = R.drawable.ic_cup,
                label = "Next award",
                value = "20 RUNE"
            )
        }
    }
}

@Composable
fun InfoItem(icon: Int, label: String, value: String) {
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

        UiSpacer(6.dp)

        Text(
            text = value,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.light,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun BondedTabContentPreview() {
    BondedTabContent(
        bondToNodeOnClick = { }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TotalBondWidgetPreview() {
    TotalBondWidget(
        onClickBondToNode = { }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun NodeListPreview() {
    NodeList(
        nodes = listOf(
            BondedNodeUiModel(
                address = "thor1abcd...xyz",
                status = "Active",
                apy = "12.5%",
                bondedAmount = "1000 RUNE",
                nextAward = "2 days",
                nextChurn = "5 days"
            ),
            BondedNodeUiModel(
                address = "thor2efgh...uvw",
                status = "Standby",
                apy = "10.2%",
                bondedAmount = "500 RUNE",
                nextAward = "1 day",
                nextChurn = "3 days"
            ),
            BondedNodeUiModel(
                address = "thor3ijkl...rst",
                status = "Active",
                apy = "11.8%",
                bondedAmount = "750 RUNE",
                nextAward = "3 days",
                nextChurn = "7 days"
            )
        )
    )
}