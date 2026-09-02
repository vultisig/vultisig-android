package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.CryptoConnectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ContentTag = "content"

class BottomNavigatorOverlayTest {

    @get:Rule val compose = createComposeRule()

    /**
     * The navigator used to live in a `Scaffold` bottom-bar slot, which pushed content up above it
     * and left an opaque band of the screen background where the last card should have been
     * (#5784). It floats over the content now, so the content still owns the full height.
     */
    @Test
    fun contentKeepsTheFullHeightUnderneathTheNavigator() {
        compose.setContent { Overlay(isNavigatorVisible = true) }
        compose.waitForIdle()

        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val contentBottom =
            compose.onNodeWithTag(ContentTag).fetchSemanticsNode().boundsInRoot.bottom

        assertEquals(rootBottom, contentBottom, 1f)
    }

    /**
     * Content drawing behind the navigator is only right as long as it can also be scrolled clear
     * of it, which is what the published padding buys — so it has to cover the navigator's own
     * height, not merely be non-zero.
     */
    @Test
    fun thePublishedPaddingCoversTheNavigator() {
        var reserved: Dp = 0.dp
        compose.setContent { Overlay(isNavigatorVisible = true) { reserved = it } }
        compose.waitForIdle()

        val navigatorHeight =
            compose.onNodeWithTag(BottomNavigatorTestTag).fetchSemanticsNode().size.height
        val reservedPx = with(compose.density) { reserved.roundToPx() }

        assertTrue(
            "reserved ${reservedPx}px does not clear the ${navigatorHeight}px navigator",
            reservedPx >= navigatorHeight,
        )
    }

    /**
     * The navigator is hidden while the search bar is open. Dropping the reservation with it would
     * reflow the whole list on every search, so the space stays booked either way.
     */
    @Test
    fun hidingTheNavigatorLeavesTheReservationInPlace() {
        var isNavigatorVisible by mutableStateOf(true)
        var reserved: Dp = 0.dp
        compose.setContent { Overlay(isNavigatorVisible = isNavigatorVisible) { reserved = it } }
        compose.waitForIdle()
        val whileVisible = reserved

        isNavigatorVisible = false
        compose.waitForIdle()

        compose.onNodeWithTag(BottomNavigatorTestTag).assertDoesNotExist()
        assertEquals(whileVisible, reserved)
    }

    @Composable
    private fun Overlay(isNavigatorVisible: Boolean, onReserved: (Dp) -> Unit = {}) {
        BottomNavigatorOverlay(
            isNavigatorVisible = isNavigatorVisible,
            activeType = CryptoConnectionType.Wallet,
            onTypeClick = {},
            onCameraClick = {},
        ) {
            onReserved(LocalBottomNavigatorPadding.current)
            Box(modifier = Modifier.fillMaxSize().testTag(ContentTag))
        }
    }
}
