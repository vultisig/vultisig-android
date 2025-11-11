package com.vultisig.wallet.ui.components.v2.fastselection

import androidx.compose.ui.geometry.Offset

data class SelectPopupUiModel<T>(
    val items: List<T>,
    val initialIndex: Int,
    val isLongPressActive: Boolean = false,
    val currentDragPosition: Offset? = null,
    val pressPosition: Offset,
)

