package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview


@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun HomePageTabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    searchTextFiledState: TextFieldState,
    onTNFTsClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onCancelSearchClick: () -> Unit,
    isTabMenu: Boolean,
) {

    AnimatedContent(
        targetState = isTabMenu,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(
                    animationSpec = tween(),
                    initialOffsetX = { fullWidth -> -fullWidth })
                        + fadeIn(animationSpec = tween()))
                    .togetherWith(
                        exit = slideOutHorizontally(
                            animationSpec = tween(),
                            targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(animationSpec = tween())
                    )
            } else {
                (slideInHorizontally(
                    animationSpec = tween(),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(animationSpec = tween()))
                    .togetherWith(
                        exit = slideOutHorizontally(
                            animationSpec = tween(),
                            targetOffsetX = { fullWidth -> -fullWidth }
                        ) + fadeOut(animationSpec = tween()))
            }

        },
    ) {
        if (it)
            HomePageTabMenu(
                modifier = modifier,
                onTNFTsClick = onTNFTsClick,
                onPortfolioClick = onPortfolioClick,
                onEditClick = onEditClick,
                onSearchClick = {
                    onSearchClick()
                },
            )
        else {
            SearchBar(
                modifier = modifier,
                state = searchTextFiledState,
                onCancelClick = {
                    onCancelSearchClick()
                },
                isInitiallyFocused = true,
            )
        }
    }

}


@Preview
@Composable
private fun PreviewHomePageTabMenuAndSearchBar() {
    HomePageTabMenuAndSearchBar(
        isTabMenu = true,
        onSearchClick = {},
        onCancelSearchClick = {},
        searchTextFiledState = rememberTextFieldState(),
    )
}


