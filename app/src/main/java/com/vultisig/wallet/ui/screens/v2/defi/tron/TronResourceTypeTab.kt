package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TronResourceTypeTab(
    selected: TronResourceType,
    onSelectionChange: (TronResourceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tron_resource_type),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(88.dp))
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val unselectedTint = Theme.v2.colors.neutrals.n100
            ResourceTypeSegment(
                label = stringResource(R.string.tron_resource_bandwidth),
                icon = R.drawable.bandwidth,
                isSelected = selected == TronResourceType.BANDWIDTH,
                selectedIconTint = Theme.v2.colors.alerts.success,
                unselectedIconTint = unselectedTint,
                onClick = { onSelectionChange(TronResourceType.BANDWIDTH) },
                modifier = Modifier.weight(1f),
            )
            ResourceTypeSegment(
                label = stringResource(R.string.tron_resource_energy),
                icon = R.drawable.energy,
                isSelected = selected == TronResourceType.ENERGY,
                selectedIconTint = Theme.v2.colors.alerts.warning,
                unselectedIconTint = unselectedTint,
                onClick = { onSelectionChange(TronResourceType.ENERGY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ResourceTypeSegment(
    label: String,
    @DrawableRes icon: Int,
    isSelected: Boolean,
    selectedIconTint: Color,
    unselectedIconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isSelected) Theme.v2.colors.backgrounds.surface2 else Color.Transparent
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(77.dp))
                .background(background)
                .semantics {
                    selected = isSelected
                    role = Role.Tab
                }
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = icon,
                size = 16.dp,
                tint = if (isSelected) selectedIconTint else unselectedIconTint,
            )
            Text(
                text = label,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Preview
@Composable
private fun TronResourceTypeTabPreview() {
    TronResourceTypeTab(selected = TronResourceType.BANDWIDTH, onSelectionChange = {})
}
