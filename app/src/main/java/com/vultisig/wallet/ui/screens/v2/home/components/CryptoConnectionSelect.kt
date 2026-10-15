package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.models.vault.BOTH_CRYPTO_CONNECTION_TYPES

/** [indicatorPosition] is the selection as a fractional tab index; see [rememberTabIndicator]. */
@Composable
internal fun CryptoConnectionSelect(
    modifier: Modifier = Modifier,
    activeType: CryptoConnectionType,
    availableCryptoTypes: List<CryptoConnectionType> = BOTH_CRYPTO_CONNECTION_TYPES,
    indicatorPosition: (() -> Float)? = null,
    onTypeClick: (CryptoConnectionType) -> Unit,
) {
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val activeIndex = availableCryptoTypes.indexOf(activeType).coerceAtLeast(0)
    val indicator =
        rememberTabIndicator(
            activeIndex = activeIndex,
            lastIndex = availableCryptoTypes.lastIndex,
            indicatorPosition = indicatorPosition,
            isReduceMotion = isReduceMotion,
        )

    TabBarSurface(tabCount = availableCryptoTypes.size, modifier = modifier) {
        SelectionPill(
            tabCount = availableCryptoTypes.size,
            position = indicator.position,
            isReduceMotion = isReduceMotion,
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            availableCryptoTypes.forEachIndexed { index, type ->
                CryptoConnectionTab(
                    modifier = Modifier.weight(1f),
                    type = type,
                    isHighlighted = index == indicator.highlightedIndex,
                    isBounceEnabled = !isReduceMotion,
                    onClick = {
                        indicator.onTap(index)
                        onTypeClick(type)
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewCryptoConnectionSelect() {
    CryptoConnectionSelect(
        activeType = CryptoConnectionType.Defi,
        onTypeClick = {},
        availableCryptoTypes = BOTH_CRYPTO_CONNECTION_TYPES,
    )
}
