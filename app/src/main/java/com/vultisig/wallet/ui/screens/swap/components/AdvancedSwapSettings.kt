package com.vultisig.wallet.ui.screens.swap.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.inputs.VsBasicTextField
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Slippage presets shown in the slippage sheet, in basis points (50 = 0.5%, 100 = 1%, 300 = 3%).
 */
private val SLIPPAGE_PRESETS_BPS = listOf(50, 100, 300)

/** Highest slippage the custom field accepts, in basis points (100% — Kyber/THOR cap). */
private const val MAX_SLIPPAGE_BPS = 10_000

/** Formats a slippage value for display: "Auto" when null, else a trimmed percent like "0.5%". */
private fun formatSlippage(bps: Int?): String? =
    bps?.let { "${BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString()}%" }

/**
 * "Advanced Settings" entry above the Swap button plus the Advanced swap sheet (#4858).
 *
 * The sheet is a single bottom sheet that pages between the row menu and the slippage selector
 * (mirroring the Figma back-navigation) rather than stacking modals. Gas limit and external
 * recipient rows are still inert; their selectors are wired in later phases.
 *
 * @param slippageBps the current slippage tolerance in basis points, or null for "Auto".
 * @param onSlippageSelected invoked with the chosen tolerance (null = Auto) — hoisted to the
 *   ViewModel so the quote re-fetches with the new value.
 */
@Composable
internal fun AdvancedSwapSettings(
    slippageBps: Int?,
    onSlippageSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        AdvancedSwapSettingsSheet(
            slippageBps = slippageBps,
            onSlippageSelected = onSlippageSelected,
            onDismiss = { isSheetVisible = false },
        )
    }
}

private enum class AdvancedPage {
    Menu,
    Slippage,
}

@Composable
private fun AdvancedSwapSettingsSheet(
    slippageBps: Int?,
    onSlippageSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(AdvancedPage.Menu) }

    V2BottomSheet(
        onDismissRequest = onDismiss,
        title =
            stringResource(
                when (page) {
                    AdvancedPage.Menu -> R.string.swap_advanced_sheet_title
                    AdvancedPage.Slippage -> R.string.swap_advanced_slippage_title
                }
            ),
        leftAction = {
            when (page) {
                AdvancedPage.Menu ->
                    VsCircleButton(
                        onClick = onDismiss,
                        icon = R.drawable.x,
                        size = VsCircleButtonSize.Small,
                        type = VsCircleButtonType.Tertiary,
                    )
                AdvancedPage.Slippage ->
                    VsCircleButton(
                        onClick = { page = AdvancedPage.Menu },
                        icon = R.drawable.ic_caret_left,
                        size = VsCircleButtonSize.Small,
                        type = VsCircleButtonType.Tertiary,
                    )
            }
        },
        rightAction = {
            if (page == AdvancedPage.Menu) {
                VsCircleButton(
                    onClick = onDismiss,
                    icon = R.drawable.check,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Primary,
                )
            }
        },
    ) {
        when (page) {
            AdvancedPage.Menu ->
                AdvancedMenu(
                    slippageValue =
                        formatSlippage(slippageBps)
                            ?: stringResource(R.string.swap_advanced_value_auto),
                    onSlippageClick = { page = AdvancedPage.Slippage },
                )
            AdvancedPage.Slippage ->
                SlippagePage(slippageBps = slippageBps, onSelect = onSlippageSelected)
        }
    }
}

@Composable
private fun AdvancedMenu(slippageValue: String, onSlippageClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp))
    ) {
        AdvancedSwapSettingRow(
            icon = R.drawable.thunder,
            title = stringResource(R.string.swap_advanced_slippage_title),
            value = slippageValue,
            onClick = onSlippageClick,
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

@Composable
private fun SlippagePage(slippageBps: Int?, onSelect: (Int?) -> Unit) {
    val isCustom = slippageBps != null && slippageBps !in SLIPPAGE_PRESETS_BPS

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.swap_slippage_helper),
            style = Theme.brockmann.body.s.regular,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
            SlippageOptionRow(
                label = stringResource(R.string.swap_advanced_value_auto),
                selected = slippageBps == null,
                onClick = { onSelect(null) },
            )
            SLIPPAGE_PRESETS_BPS.forEach { bps ->
                AdvancedSwapSettingDivider()
                SlippageOptionRow(
                    label = formatSlippage(bps).orEmpty(),
                    selected = slippageBps == bps,
                    onClick = { onSelect(bps) },
                )
            }
            AdvancedSwapSettingDivider()
            SlippageCustomRow(isSelected = isCustom, currentBps = slippageBps, onSelect = onSelect)
        }
    }
}

@Composable
private fun SlippageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(all = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(1f),
        )
        SelectedCheck(selected)
    }
}

@Composable
private fun SlippageCustomRow(isSelected: Boolean, currentBps: Int?, onSelect: (Int?) -> Unit) {
    // Seed with the current custom percent so reopening the sheet shows what was entered.
    val customState =
        rememberTextFieldState(
            if (isSelected) formatSlippage(currentBps)?.dropLast(1) ?: "" else ""
        )

    // Parse percent → bps on each edit and hoist it up; rapid typing coalesces in the quote
    // pipeline's debounce. Blank/invalid input leaves the selection untouched.
    LaunchedEffect(Unit) {
        snapshotFlow { customState.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                val percent = text.toBigDecimalOrNull() ?: return@collect
                val bps = percent.movePointRight(2).toDouble().roundToInt()
                if (bps in 1..MAX_SLIPPAGE_BPS) onSelect(bps)
            }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(all = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VsBasicTextField(
            textFieldState = customState,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            hint = stringResource(R.string.swap_slippage_custom_hint),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "%",
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        SelectedCheck(isSelected)
    }
}

@Composable
private fun SelectedCheck(selected: Boolean) {
    if (selected) {
        UiIcon(
            drawableResId = R.drawable.check,
            size = 20.dp,
            tint = Theme.v2.colors.primary.accent4,
        )
    } else {
        Box(modifier = Modifier.padding(10.dp))
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
