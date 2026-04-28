package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.screens.v2.defi.ActionButton
import com.vultisig.wallet.ui.theme.Theme

private val TronFreezeCardIconCircleColor = Color.White.copy(alpha = 0.12f)

@Composable
internal fun TronFreezePositionCard(
    frozenTotalPrice: String,
    frozenTotalTrx: String,
    isBalanceVisible: Boolean,
    isUnfreezeEnabled: Boolean,
    onClickFreeze: () -> Unit,
    onClickUnfreeze: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(1.dp, Theme.v2.colors.border.normal, RoundedCornerShape(16.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: logo + title + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(drawableResId = R.drawable.tron, size = 42.dp)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.tron_defi_tron_freeze),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = if (isBalanceVisible) frozenTotalPrice else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.headings.title1,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)

        // Frozen amount + action buttons
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.tron_defi_frozen),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = if (isBalanceVisible) "$frozenTotalTrx TRX" else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    title = stringResource(R.string.tron_defi_unfreeze),
                    icon = R.drawable.circle_minus,
                    background = Theme.v2.colors.backgrounds.tertiary_2,
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = TronFreezeCardIconCircleColor,
                    enabled = isUnfreezeEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onClickUnfreeze,
                )
                ActionButton(
                    title = stringResource(R.string.tron_defi_freeze),
                    icon = R.drawable.circle_plus,
                    background = Theme.v2.colors.buttons.ctaPrimary,
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = TronFreezeCardIconCircleColor,
                    modifier = Modifier.weight(1f),
                    onClick = onClickFreeze,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TronFreezePositionCardPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        TronFreezePositionCard(
            frozenTotalPrice = "$4,800",
            frozenTotalTrx = "800.000000",
            isBalanceVisible = true,
            isUnfreezeEnabled = true,
            onClickFreeze = {},
            onClickUnfreeze = {},
        )
    }
}
