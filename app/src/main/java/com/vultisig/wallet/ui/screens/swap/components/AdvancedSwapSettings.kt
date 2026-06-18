package com.vultisig.wallet.ui.screens.swap.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme

/**
 * "Advanced Settings" entry above the Swap button plus the Advanced swap sheet (#4858).
 *
 * The sheet shows the three per-swap override rows with their current values. The rows are inert
 * here — their sub-sheets (slippage / gas limit / external recipient) and the confirm behavior are
 * wired in later phases. Both header buttons currently just dismiss the sheet.
 */
@Composable
internal fun AdvancedSwapSettings(modifier: Modifier = Modifier) {
    var isSheetVisible by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.swap_advanced_settings),
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.tertiary,
        textAlign = TextAlign.Center,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.fillMaxWidth().clickable { isSheetVisible = true }.padding(8.dp),
    )

    if (isSheetVisible) {
        AdvancedSwapSettingsSheet(onDismiss = { isSheetVisible = false })
    }
}

@Composable
private fun AdvancedSwapSettingsSheet(onDismiss: () -> Unit) {
    V2BottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.swap_advanced_sheet_title),
        leftAction = {
            VsCircleButton(
                onClick = onDismiss,
                icon = R.drawable.x,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Tertiary,
            )
        },
        rightAction = {
            VsCircleButton(
                onClick = onDismiss,
                icon = R.drawable.check,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Primary,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp))
        ) {
            AdvancedSwapSettingRow(
                icon = R.drawable.thunder,
                title = stringResource(R.string.swap_advanced_slippage_title),
                value = stringResource(R.string.swap_advanced_value_auto),
            )
            AdvancedSwapSettingDivider()
            AdvancedSwapSettingRow(
                icon = R.drawable.gas,
                title = stringResource(R.string.swap_advanced_gas_limit_title),
                value = stringResource(R.string.swap_advanced_value_auto),
            )
            AdvancedSwapSettingDivider()
            AdvancedSwapSettingRow(
                icon = R.drawable.ic_external_recipient,
                title = stringResource(R.string.swap_advanced_external_recipient_title),
                value = stringResource(R.string.swap_advanced_value_off),
            )
        }
    }
}

@Composable
private fun AdvancedSwapSettingRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(all = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UiIcon(drawableResId = icon, size = 16.dp, tint = Theme.v2.colors.text.secondary)
        Text(
            text = title,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        UiIcon(
            drawableResId = R.drawable.ic_chevron_right_small,
            size = 24.dp,
            tint = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun AdvancedSwapSettingDivider() {
    HorizontalDivider(thickness = 1.dp, color = Theme.v2.colors.border.normal)
}
