package com.vultisig.wallet.ui.screens.swap.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
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
import com.vultisig.wallet.ui.components.v2.bottomsheets.DragHandler
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.swap.components.AdvancedMenu
import com.vultisig.wallet.ui.screens.swap.components.ExternalRecipientPage
import com.vultisig.wallet.ui.screens.swap.components.GasLimitPage
import com.vultisig.wallet.ui.screens.swap.components.SlippagePage
import com.vultisig.wallet.ui.theme.Theme

/**
 * Previews for the Advanced swap settings sheet (#4858). The real sheet is a
 * [androidx.compose.material3.ModalBottomSheet] that animates in over a separate window, so it
 * can't be captured reliably in a static render. Each page is instead docked at the bottom inside
 * [PreviewAdvancedSheet] — a hand-assembled stand-in that mirrors `V2BottomSheet`'s chrome (drag
 * handle, rounded top, back/title/done top row) over the dimmed Swap screen, so the screenshots
 * look like the real app.
 */
@Preview
@Composable
internal fun AdvancedMenuPreview() {
    PreviewAdvancedSheet(
        title = stringResource(R.string.swap_advanced_sheet_title),
        isMenu = true,
    ) {
        AdvancedMenu(
            slippageValue = stringResource(R.string.swap_advanced_value_auto),
            gasLimitValue = stringResource(R.string.swap_advanced_value_auto),
            isGasLimitApplicable = true,
            externalRecipientValue = stringResource(R.string.swap_advanced_value_off),
            onSlippageClick = {},
            onGasLimitClick = {},
            onExternalRecipientClick = {},
        )
    }
}

// Configured menu: custom slippage + manual gas limit + recipient on, mirroring a non-EVM source
// where the Gas Limit row is dimmed and disabled.
@Preview
@Composable
internal fun AdvancedMenuConfiguredPreview() {
    PreviewAdvancedSheet(
        title = stringResource(R.string.swap_advanced_sheet_title),
        isMenu = true,
    ) {
        AdvancedMenu(
            slippageValue = "1%",
            gasLimitValue = stringResource(R.string.swap_advanced_value_auto),
            isGasLimitApplicable = false,
            externalRecipientValue = stringResource(R.string.swap_advanced_value_on),
            onSlippageClick = {},
            onGasLimitClick = {},
            onExternalRecipientClick = {},
        )
    }
}

@Preview
@Composable
internal fun AdvancedSlippagePreview() {
    PreviewAdvancedSheet(title = stringResource(R.string.swap_advanced_slippage_title)) {
        SlippagePage(slippageBps = 100, onSelect = {})
    }
}

@Preview
@Composable
internal fun AdvancedGasLimitPreview() {
    PreviewAdvancedSheet(title = stringResource(R.string.swap_advanced_gas_limit_title)) {
        GasLimitPage(gasLimitOverride = 210000, onSelect = {})
    }
}

@Preview
@Composable
internal fun AdvancedExternalRecipientPreview() {
    PreviewAdvancedSheet(title = stringResource(R.string.swap_advanced_external_recipient_title)) {
        ExternalRecipientPage(
            externalRecipient = "0x1234",
            errorText = stringResource(R.string.swap_external_recipient_invalid),
            onSelect = {},
        )
    }
}

/**
 * Docks [content] at the bottom inside a static replica of the Advanced sheet chrome, over a dimmed
 * Swap screen. [isMenu] selects the close icon (menu page) vs. the back caret (sub-pages) for the
 * left action.
 */
@Composable
private fun PreviewAdvancedSheet(
    title: String,
    isMenu: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        // Stand-in for the Swap screen visible behind the sheet.
        SwapScreen(state = SwapFormUiModel(), srcAmountTextFieldState = TextFieldState())

        // Material's bottom-sheet scrim is black @ ~32% alpha.
        Box(modifier = Modifier.fillMaxSize().background(Color(0x52000000)))

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Theme.v2.colors.backgrounds.primary)
                        .padding(all = 16.dp)
            ) {
                PreviewSheetTopRow(title = title, isMenu = isMenu)
                Box(modifier = Modifier.fillMaxWidth(), content = content)
            }
            DragHandler(
                modifier = Modifier.padding(top = 8.dp).align(Alignment.TopCenter),
                color = Theme.v2.colors.vibrant.primary,
            )
        }
    }
}

@Composable
private fun PreviewSheetTopRow(title: String, isMenu: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (isMenu) {
                VsCircleButton(
                    onClick = {},
                    drawableResId = R.drawable.big_close,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Tertiary,
                )
            } else {
                VsCircleButton(
                    onClick = {},
                    icon = R.drawable.ic_caret_left,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Tertiary,
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.neutrals.n100,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            VsCircleButton(
                onClick = {},
                drawableResId = R.drawable.big_tick,
                size = VsCircleButtonSize.Small,
            )
        }
    }
}
