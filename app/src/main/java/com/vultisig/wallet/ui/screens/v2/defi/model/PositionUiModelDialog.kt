package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.ImageModel

internal data class PositionUiModelDialog(
    val logo: ImageModel,
    val ticker: String,
    val isSelected: Boolean = true,
)