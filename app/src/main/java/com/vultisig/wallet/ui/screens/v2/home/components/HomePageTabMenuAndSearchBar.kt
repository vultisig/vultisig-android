package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.components.v2.tab.TabMenuAndSearchBar


@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun HomePageTabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    searchTextFieldState: TextFieldState,
//    onTNFTsClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onCancelSearchClick: () -> Unit,
    isTabMenu: Boolean,
) {
    TabMenuAndSearchBar(
        modifier = modifier,
        isTabMenu = isTabMenu,
        searchTextFieldState = searchTextFieldState,
        onCancelSearchClick = onCancelSearchClick,
        isInitiallyFocused = true,
        tabMenuContent = {
            HomePageTabMenu(
                modifier = modifier,
//                onTNFTsClick = onTNFTsClick,
                onPortfolioClick = onPortfolioClick,
                onEditClick = onEditClick,
                onSearchClick = {
                    onSearchClick()
                },
            )
        },
    )
}


@Preview
@Composable
private fun PreviewHomePageTabMenuAndSearchBar() {
    HomePageTabMenuAndSearchBar(
        isTabMenu = true,
        onSearchClick = {},
        onCancelSearchClick = {},
        searchTextFieldState = rememberTextFieldState(),
    )
}


