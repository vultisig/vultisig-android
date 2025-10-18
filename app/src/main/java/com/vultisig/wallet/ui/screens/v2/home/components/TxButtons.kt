package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TxButtons(
    modifier: Modifier = Modifier,
    isSwapEnabled: Boolean,
    onSend: () -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            20.dp,
            Alignment.CenterHorizontally
        )
    ) {
        TransactionTypeButton(
            txType = TransactionType.SEND,
            onClick = onSend
        )

        if (isSwapEnabled) {
            TransactionTypeButton(
                txType = TransactionType.SWAP,
                onClick = onSwap
            )
        }
    }
}