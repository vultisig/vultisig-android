package com.vultisig.wallet.ui.screens.v2.chaintokens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.BottomNavigatorOverlay
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChainTokensListHeightTest {

    @get:Rule val compose = createComposeRule()

    /**
     * The token list wraps its content, so the room reserved for the floating navigator has to be
     * taken on the column hosting the card and never as padding inside the list — inside, a chain
     * holding one token renders a card with a tall band of dead space under its only row.
     */
    @Test
    fun aSingleTokenCardHugsItsRow() {
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
                                chainName = "Bitcoin",
                                chainAddress = "bc1ql2v0000000000000000000000000j4g60yc",
                                totalBalance = "$15.16",
                                tokens =
                                    listOf(
                                        ChainTokenUiModel(
                                            id = "BTC-Bitcoin",
                                            name = "BTC",
                                            balance = "0.00019793 BTC",
                                            fiatBalance = "$15.16",
                                        )
                                    ),
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

        val card = compose.onNodeWithTag(TokenListTestTag).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText("BTC").fetchSemanticsNode().boundsInRoot
        val deadSpaceBelowTheRow = card.bottom - row.bottom
        val allowance = with(compose.density) { CardPaddingAllowance.toPx() }

        assertTrue(
            "the card runs ${deadSpaceBelowTheRow}px past its only row",
            deadSpaceBelowTheRow <= allowance,
        )
    }

    private companion object {
        /** The row's own vertical padding plus the card's inset — anything beyond this is slack. */
        val CardPaddingAllowance = 48.dp
    }
}
