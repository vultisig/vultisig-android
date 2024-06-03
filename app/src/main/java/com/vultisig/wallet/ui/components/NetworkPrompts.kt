package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.ClickOnce
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.presenter.common.clickOnce
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NetworkPrompts(
    modifier: Modifier = Modifier,
    networkPromptOption: NetworkPromptOption = NetworkPromptOption.WIFI,
    onChange: (NetworkPromptOption) -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        NetworkChip(
            title = stringResource(R.string.network_prompts_wifi),
            drawableResId = R.drawable.wifi,
            isSelected = networkPromptOption == NetworkPromptOption.WIFI,
            onClick = {
                onChange(NetworkPromptOption.WIFI)
            },
        )

        NetworkChip(
            title = stringResource(R.string.network_prompts_hotspot),
            drawableResId = R.drawable.baseline_wifi_tethering_24,
            isSelected = networkPromptOption == NetworkPromptOption.HOTSPOT,
            onClick = {
                onChange(NetworkPromptOption.HOTSPOT)
            },
        )


        NetworkChip(
            title = stringResource(R.string.network_prompts_cellular),
            drawableResId = R.drawable.baseline_signal_cellular_alt_24,
            isSelected = networkPromptOption == NetworkPromptOption.CELLULAR,
            onClick = {
                onChange(NetworkPromptOption.CELLULAR)
            },
        )
    }
}

@Composable
private fun NetworkChip(
    title: String,
    @DrawableRes drawableResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected)
            BorderStroke(1.dp, Theme.colors.neutral0)
        else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Theme.colors.oxfordBlue400,
            selectedContainerColor = Theme.colors.oxfordBlue200,
        ),
        onClick = ClickOnce {onClick},
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UiIcon(
                    drawableResId = drawableResId,
                    size = 20.dp,
                    tint = Theme.colors.turquoise600Main,
                )

                Text(
                    text = title,
                    style = Theme.menlo.caption,
                    color = Theme.colors.neutral100,
                )
            }
        })
}

@Preview
@Composable
private fun PreviewNetworkPrompts() {
    NetworkPrompts()
}