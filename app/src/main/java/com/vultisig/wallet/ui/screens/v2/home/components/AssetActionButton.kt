package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

enum class AssetAction(
    @DrawableRes internal val iconRes: Int,
    @StringRes internal val titleRes: Int,
) {
    SWAP(R.drawable.swap_v2, R.string.transaction_type_button_swap),
    BUY(R.drawable.buy, R.string.transaction_type_button_buy),
    SEND(R.drawable.send, R.string.transaction_type_button_send),
    RECEIVE(R.drawable.receive, R.string.transaction_type_button_receive),
    FUNCTIONS(R.drawable.functions, R.string.transaction_type_button_functions),
}

/**
 * Height of a single [AssetActionButton]. Rows built from flags that resolve asynchronously should
 * reserve this, so the row is not zero-height on the first frame — a container sized by its content
 * (a bottom sheet, above all) re-anchors when the buttons appear.
 */
internal val assetActionButtonHeight: Dp
    @Composable
    get() =
        AssetActionIconBoxSize +
            IconLabelSpacing +
            with(LocalDensity.current) { Theme.brockmann.supplementary.caption.lineHeight.toDp() }

/** Side of the icon box when the row has the width to give every button its full size. */
internal val AssetActionIconBoxSize = 52.dp

private val IconLabelSpacing = 8.dp

@Composable
fun AssetActionButton(
    modifier: Modifier = Modifier,
    action: AssetAction,
    isSelected: Boolean =
        when (action) {
            AssetAction.SWAP -> true
            else -> false
        },
    iconBoxSize: Dp = AssetActionIconBoxSize,
    onClick: () -> Unit = {},
) {

    val backgroundColor =
        if (isSelected) Theme.v2.colors.buttons.ctaPrimary
        else Theme.v2.colors.backgrounds.tertiary_2

    Column(
        modifier = modifier.clickOnce(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.size(size = iconBoxSize)
                    .clip(shape = Theme.v2.radius.lg)
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.neutrals.n100.copy(alpha = 0.03f),
                        shape = Theme.v2.radius.lg,
                    )
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(
                drawableResId = action.iconRes,
                size = 20.dp,
                tint = Theme.v2.colors.text.primary,
            )
        }

        UiSpacer(IconLabelSpacing)

        Text(
            text = stringResource(action.titleRes),
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.supplementary.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun PreviewAssetActionButtonSwap() {
    AssetActionButton(action = AssetAction.SWAP, isSelected = true)
}

@Preview
@Composable
private fun PreviewAssetActionButtonSend() {
    AssetActionButton(action = AssetAction.SEND, isSelected = false)
}
