package com.vultisig.wallet.ui.screens.swap.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.inputs.VsBasicTextField
import com.vultisig.wallet.ui.components.library.form.VsUiCheckbox
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
private fun formatSlippage(bps: Int?): String? = bps?.let {
    "${BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString()}%"
}

/**
 * The centered, underlined "Advanced Settings" entry shown above the Swap button.
 *
 * The sheet it opens ([AdvancedSwapSettingsSheet]) is hosted separately at the screen-content level
 * rather than next to this link — the link lives in the bottom bar, whose content is swapped by an
 * `AnimatedContent` on keyboard visibility, and hosting the sheet there would unmount it (closing
 * it) the moment a field inside it opens the keyboard (#4858).
 */
@Composable
internal fun AdvancedSettingsLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.swap_advanced_settings),
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.tertiary,
        textAlign = TextAlign.Center,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
    )
}

private enum class AdvancedPage {
    Menu,
    Slippage,
    GasLimit,
    ExternalRecipient,
}

/**
 * The Advanced swap sheet (#4858): a single bottom sheet that pages between the row menu and the
 * slippage / gas-limit / external-recipient selectors (mirroring the Figma back-navigation) rather
 * than stacking modals.
 *
 * @param slippageBps the current slippage tolerance in basis points, or null for "Auto".
 * @param onSlippageSelected invoked with the chosen tolerance (null = Auto) — hoisted to the
 *   ViewModel so the quote re-fetches with the new value.
 * @param gasLimitOverride the current EVM gas-limit override (units), or null for "Auto".
 * @param isGasLimitApplicable whether the source chain is EVM; the Gas Limit row is disabled
 *   otherwise.
 * @param onGasLimitSelected invoked with the chosen gas limit (null = Auto).
 * @param externalRecipient the current external recipient address, or null/blank = off.
 * @param onExternalRecipientSelected invoked with the entered address (null/blank = off).
 */
@Composable
internal fun AdvancedSwapSettingsSheet(
    slippageBps: Int?,
    onSlippageSelected: (Int?) -> Unit,
    gasLimitOverride: Long?,
    isGasLimitApplicable: Boolean,
    onGasLimitSelected: (Long?) -> Unit,
    externalRecipient: String?,
    externalRecipientError: String?,
    onExternalRecipientSelected: (String?) -> Unit,
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
                    AdvancedPage.GasLimit -> R.string.swap_advanced_gas_limit_title
                    AdvancedPage.ExternalRecipient ->
                        R.string.swap_advanced_external_recipient_title
                }
            ),
        leftAction = {
            if (page == AdvancedPage.Menu) {
                VsCircleButton(
                    onClick = onDismiss,
                    drawableResId = R.drawable.big_close,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Tertiary,
                )
            } else {
                VsCircleButton(
                    onClick = { page = AdvancedPage.Menu },
                    icon = R.drawable.ic_caret_left,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Tertiary,
                )
            }
        },
        rightAction = {
            // The "done" check is present on every page in Figma (menu + each selector). Selections
            // apply live, so it simply confirms and closes the whole sheet.
            VsCircleButton(
                onClick = onDismiss,
                drawableResId = R.drawable.big_tick,
                size = VsCircleButtonSize.Small,
            )
        },
    ) {
        val autoLabel = stringResource(R.string.swap_advanced_value_auto)
        when (page) {
            AdvancedPage.Menu ->
                AdvancedMenu(
                    slippageValue = formatSlippage(slippageBps) ?: autoLabel,
                    gasLimitValue = gasLimitOverride?.toString() ?: autoLabel,
                    isGasLimitApplicable = isGasLimitApplicable,
                    externalRecipientValue =
                        if (externalRecipient.isNullOrBlank())
                            stringResource(R.string.swap_advanced_value_off)
                        else stringResource(R.string.swap_advanced_value_on),
                    onSlippageClick = { page = AdvancedPage.Slippage },
                    onGasLimitClick = { page = AdvancedPage.GasLimit },
                    onExternalRecipientClick = { page = AdvancedPage.ExternalRecipient },
                )
            AdvancedPage.Slippage ->
                SlippagePage(slippageBps = slippageBps, onSelect = onSlippageSelected)
            AdvancedPage.GasLimit ->
                GasLimitPage(gasLimitOverride = gasLimitOverride, onSelect = onGasLimitSelected)
            AdvancedPage.ExternalRecipient ->
                ExternalRecipientPage(
                    externalRecipient = externalRecipient,
                    errorText = externalRecipientError,
                    onSelect = onExternalRecipientSelected,
                )
        }
    }
}

@Composable
private fun AdvancedMenu(
    slippageValue: String,
    gasLimitValue: String,
    isGasLimitApplicable: Boolean,
    externalRecipientValue: String,
    onSlippageClick: () -> Unit,
    onGasLimitClick: () -> Unit,
    onExternalRecipientClick: () -> Unit,
) {
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
            value = gasLimitValue,
            enabled = isGasLimitApplicable,
            onClick = onGasLimitClick,
        )
        AdvancedSwapSettingDivider()
        AdvancedSwapSettingRow(
            icon = R.drawable.ic_external_recipient,
            title = stringResource(R.string.swap_advanced_external_recipient_title),
            value = externalRecipientValue,
            onClick = onExternalRecipientClick,
        )
    }
}

@Composable
private fun ExternalRecipientPage(
    externalRecipient: String?,
    errorText: String?,
    onSelect: (String?) -> Unit,
) {
    val state = rememberTextFieldState(externalRecipient.orEmpty())

    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text -> onSelect(text.trim().takeIf { it.isNotEmpty() }) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.swap_external_recipient_helper),
            style = Theme.brockmann.body.s.regular,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VsBasicTextField(
                textFieldState = state,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                hint = stringResource(R.string.swap_external_recipient_hint),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            PasteIcon(
                modifier = Modifier.padding(4.dp),
                onPaste = { state.setTextAndPlaceCursorAtEnd(it.trim()) },
            )
        }
        // Invalid-address feedback: the entered recipient isn't a valid address for the destination
        // chain. The swap is also blocked at sign time, but surface it here so the user can fix it.
        if (errorText != null) {
            Text(
                text = errorText,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
        VsUiCheckbox(checked = selected, onCheckedChange = { onClick() })
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Figma shows a static "Custom" label followed by the editable "0.00%" value, with the
        // same circular checkbox the preset rows use on the trailing edge.
        Text(
            text = stringResource(R.string.swap_slippage_custom_hint),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        VsBasicTextField(
            textFieldState = customState,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            hint = stringResource(R.string.swap_slippage_custom_value_hint),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        VsUiCheckbox(checked = isSelected, onCheckedChange = {})
    }
}

@Composable
private fun AdvancedSwapSettingRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    // Dim and disable rows whose setting does not apply to the current swap (e.g. gas limit on a
    // non-EVM source).
    val titleColor = if (enabled) Theme.v2.colors.text.secondary else Theme.v2.colors.text.tertiary
    val valueColor = if (enabled) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(all = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UiIcon(drawableResId = icon, size = 16.dp, tint = titleColor)
        Text(
            text = title,
            style = Theme.brockmann.body.s.medium,
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = Theme.brockmann.body.s.medium, color = valueColor)
        UiIcon(drawableResId = R.drawable.ic_chevron_right_small, size = 24.dp, tint = valueColor)
    }
}

@Composable
private fun GasLimitPage(gasLimitOverride: Long?, onSelect: (Long?) -> Unit) {
    // Seed with the current override; blank means Auto (use the aggregator estimate).
    val state = rememberTextFieldState(gasLimitOverride?.toString().orEmpty())

    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text -> onSelect(text.toLongOrNull()?.takeIf { it > 0 }) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.swap_gas_limit_helper),
            style = Theme.brockmann.body.s.regular,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        VsBasicTextField(
            textFieldState = state,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            hint = stringResource(R.string.swap_advanced_value_auto),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun AdvancedSwapSettingDivider() {
    HorizontalDivider(thickness = 1.dp, color = Theme.v2.colors.border.normal)
}
