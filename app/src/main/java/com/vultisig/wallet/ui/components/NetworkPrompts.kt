package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun NetworkPrompts(
    modifier: Modifier = Modifier,
    networkPromptOption: NetworkPromptOption = NetworkPromptOption.LOCAL,
    onChange: (NetworkPromptOption) -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        NetworkChip(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.network_prompts_internet),
            drawableResId = R.drawable.baseline_signal_cellular_alt_24,
            isSelected = networkPromptOption == NetworkPromptOption.INTERNET,
            onClick = {
                onChange(NetworkPromptOption.INTERNET)
            },
        )

        NetworkChip(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.network_prompts_local),
            drawableResId = R.drawable.wifi,
            isSelected = networkPromptOption == NetworkPromptOption.LOCAL,
            onClick = {
                onChange(NetworkPromptOption.LOCAL)
            },
        )
    }
}

@Composable
private fun NetworkChip(
    title: String,
    modifier: Modifier,
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
        onClick = clickOnce(onClick),
        modifier = modifier,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                UiIcon(
                    drawableResId = drawableResId,
                    size = 20.dp,
                    tint = Theme.colors.turquoise600Main,
                )

                UiSpacer(size = 8.dp)

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