package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.ImageModel

data class PositionUiModelDialog(
    val logo: ImageModel,
    val ticker: String,
    val isSelected: Boolean = true,
    val positionKey: String = ticker,
)
