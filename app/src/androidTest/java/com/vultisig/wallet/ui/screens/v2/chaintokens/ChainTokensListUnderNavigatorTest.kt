package com.vultisig.wallet.ui.screens.v2.chaintokens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.BottomNavigatorOverlay
import com.vultisig.wallet.ui.screens.v2.home.components.BottomNavigatorTestTag
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChainTokensListUnderNavigatorTest {

    @get:Rule val compose = createComposeRule()

    /**
     * The navigator floats over the screen, so the token list has to run to the bottom of the
     * window and pass underneath it rather than stop short above an empty band.
     */
    @Test
    fun theListRunsBehindTheNavigator() {
        setScreen(tokens = tokens(count = 1))

        val list = compose.onNodeWithTag(TokenListTestTag).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot

        assertEquals(root.bottom, list.bottom, 1f)
    }

    /** The room the navigator covers is scroll padding, so the last token can still clear it. */
    @Test
    fun theLastTokenScrollsClearOfThePill() {
        val tokens = tokens(count = 30)
        setScreen(tokens = tokens)

        compose.onNodeWithTag(TokenListTestTag).performScrollToIndex(tokens.lastIndex)
        compose.waitForIdle()

        val lastRow = compose.onNodeWithText(tokens.last().name).fetchSemanticsNode().boundsInRoot
        val navigator =
            compose.onNodeWithTag(BottomNavigatorTestTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "the last token ends at ${lastRow.bottom}px, under the pill starting at ${navigator.top}px",
            lastRow.bottom <= navigator.top,
        )
    }

    private fun tokens(count: Int) =
        List(count) { index ->
            ChainTokenUiModel(
                id = "TOKEN$index-Ethereum",
                name = "TOKEN$index",
                balance = "1.0 TOKEN$index",
                fiatBalance = "$1.00",
            )
        }

    private fun setScreen(tokens: List<ChainTokenUiModel>) {
        compose.setContent {
            OnBoardingComposeTheme {
                BottomNavigatorOverlay(
                    isNavigatorVisible = true,
                    activeType = CryptoConnectionType.Wallet,
                    onTypeClick = {},
                    onCameraClick = {},
                ) {
                    ChainTokensScreen(
                        uiModel =
                            ChainTokensUiModel(
                                chainName = "Ethereum",
                                chainAddress = "0x0000000000000000000000000000000000000000",
                                totalBalance = "$30.00",
                                tokens = tokens,
                            ),
                        onBackClick = {},
                        onRefresh = {},
                        onShowSearchBar = {},
                        onHideSearchBar = {},
                        onSend = {},
                        onSwap = {},
                        onBuy = {},
                        onDeposit = {},
                        onReceive = {},
                        onHistory = {},
                        onSelectTokens = {},
                        onTokenClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}
