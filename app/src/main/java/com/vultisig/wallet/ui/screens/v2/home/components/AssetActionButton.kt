package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

enum class AssetAction {
    SWAP,
    BUY,
    SEND,
    RECEIVE,
    FUNCTIONS,
    HISTORY,
}

@Composable
fun AssetActionButton(
    modifier: Modifier = Modifier,
    action: AssetAction,
    isSelected: Boolean =
        when (action) {
            AssetAction.SWAP -> true
            else -> false
        },
    onClick: () -> Unit = {},
) {

    val backgroundColor =
        if (isSelected) Theme.v2.colors.buttons.ctaPrimary
        else Theme.v2.colors.backgrounds.tertiary_2

    val (logo, title) =
        when (action) {
            AssetAction.SWAP -> R.drawable.swap_v2 to R.string.transaction_type_button_swap
            AssetAction.BUY -> R.drawable.buy to R.string.transaction_type_button_buy
            AssetAction.SEND -> R.drawable.send to R.string.transaction_type_button_send
            AssetAction.RECEIVE -> R.drawable.receive to R.string.transaction_type_button_receive
            AssetAction.FUNCTIONS ->
                R.drawable.functions to R.string.transaction_type_button_functions
            AssetAction.HISTORY ->
                R.drawable.calendar_clock to R.string.transaction_type_button_history
        }

    Column(
        modifier = modifier.clickOnce(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.size(size = 52.dp)
                    .clip(shape = RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.neutrals.n100.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(drawableResId = logo, size = 20.dp, tint = Theme.v2.colors.text.primary)
        }

        UiSpacer(8.dp)

        Text(
            text = stringResource(title),
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.supplementary.caption,
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
