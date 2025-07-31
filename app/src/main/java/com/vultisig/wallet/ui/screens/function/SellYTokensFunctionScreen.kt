package com.vultisig.wallet.ui.screens.function

import androidx.compose.runtime.Composable
import com.vultisig.wallet.ui.components.library.form.FormSelection

@Composable
internal fun SellYTokensFunctionScreen(
    selectedSlippage: String,
    slippageOptions: List<String>,
    onSelectSlippage: (String) -> Unit,
    onAmountLostFocus: () -> Unit,
) {
    FormSelection(
        selected = selectedSlippage,
        options = slippageOptions,
        onSelectOption = onSelectSlippage,
        mapTypeToString = { it },
    )
}