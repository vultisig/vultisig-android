package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.v2.visuals.BottomFadeEffect

/**
 * Height of the floating navigator: the 64dp Wallet/DeFi pill plus the 1dp gradient ring drawn
 * around it. The camera button is shorter (62dp), so the pill sets the height.
 */
private val NavigatorHeight = 66.dp

/** Breathing room between the last row of content and the pill, mirroring iOS `VultiTabBar`. */
private val NavigatorContentSpacing = 16.dp

/**
 * Bottom space the floating navigator covers on the screen hosting it.
 *
 * The navigator is drawn as an overlay rather than in a `Scaffold` bottom-bar slot, so content runs
 * behind it instead of stopping short above an opaque block. Scrollable content anywhere inside a
 * host reads this and adds it as bottom padding, so its last row can still be scrolled clear of the
 * pill.
 */
internal val LocalBottomNavigatorPadding = staticCompositionLocalOf { 0.dp }

internal const val BottomNavigatorTestTag = "bottom_navigator"

/**
 * Draws [content] full-bleed with the Wallet/DeFi pill and the camera button floating over its
 * bottom edge, above a fade into the background so content passing underneath stays legible.
 */
@Composable
internal fun BottomNavigatorOverlay(
    isNavigatorVisible: Boolean,
    activeType: CryptoConnectionType,
    onTypeClick: (CryptoConnectionType) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
    availableCryptoTypes: List<CryptoConnectionType> = BOTH_CRYPTO_CONNECTION_TYPES,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Reserved whether or not the navigator is currently shown, so content does not reflow when
        // it is hidden for the search bar.
        CompositionLocalProvider(
            LocalBottomNavigatorPadding provides NavigatorHeight + NavigatorContentSpacing
        ) {
            content()
        }

        if (isNavigatorVisible) {
            BottomFadeEffect(
                height = NavigatorHeight + NavigatorContentSpacing,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .testTag(BottomNavigatorTestTag),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CryptoConnectionSelect(
                    activeType = activeType,
                    availableCryptoTypes = availableCryptoTypes,
                    onTypeClick = onTypeClick,
                )
                CameraButton(onClick = onCameraClick)
            }
        }
    }
}
