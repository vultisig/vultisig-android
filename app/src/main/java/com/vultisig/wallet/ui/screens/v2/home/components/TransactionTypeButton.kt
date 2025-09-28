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

enum class TransactionType {
    SWAP,
    BUY,
    SEND,
    RECEIVE,
    FUNCTIONS,
}


@Composable
fun TransactionTypeButton(
    modifier: Modifier = Modifier,
    txType: TransactionType,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {

    val backgroundColor = if (isSelected)
        Theme.colors.buttons.primary
    else Theme.colors.backgrounds.tertiary

    val (logo, title) = when (txType) {
        TransactionType.SWAP -> R.drawable.swap to R.string.transaction_type_button_swap
        TransactionType.BUY -> R.drawable.buy to R.string.transaction_type_button_buy
        TransactionType.SEND -> R.drawable.send to R.string.transaction_type_button_send
        TransactionType.RECEIVE -> R.drawable.receive to R.string.transaction_type_button_receive
        TransactionType.FUNCTIONS -> R.drawable.functions to R.string.transaction_type_button_functions
    }


    Column(
        modifier = modifier
            .clickOnce(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        Box(
            modifier = Modifier
                .size(
                    size = 52.dp
                )
                .clip(
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Theme.colors.neutrals.n100.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            UiIcon(
                drawableResId = logo,
                size = 24.dp,
                tint = Theme.colors.text.primary
            )
        }

        UiSpacer(
            4.dp
        )

        Text(
            text = stringResource(title),
            color = Theme.colors.text.primary,
            style = Theme.brockmann.supplementary.caption
        )
    }
}

@Preview
@Composable
private fun PreviewTransactionTypeButton() {
    TransactionTypeButton(
        txType = TransactionType.SWAP,
        isSelected = true
    )
}

@Preview
@Composable
private fun PreviewTransactionTypeButton2() {
    TransactionTypeButton(
        txType = TransactionType.SEND,
        isSelected = false
    )
}