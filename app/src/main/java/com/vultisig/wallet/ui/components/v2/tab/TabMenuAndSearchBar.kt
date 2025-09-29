package com.vultisig.wallet.ui.components.v2.tab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar

@Composable
internal fun TabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    isTabMenu: Boolean,
    searchTextFieldState: TextFieldState,
    onCancelSearchClick: () -> Unit,
    isInitiallyFocused: Boolean,
    tabMenuContent: @Composable () -> Unit,
){
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
            tabMenuContent()
        else {
            SearchBar(
                modifier = modifier,
                state = searchTextFieldState,
                onCancelClick = {
                    onCancelSearchClick()
                },
                isInitiallyFocused = isInitiallyFocused,
            )
        }
    }
}

@Preview
@Composable
private fun TabMenuAndSearchBarPreview(){
    TabMenuAndSearchBar(
        isTabMenu = false,
        searchTextFieldState = rememberTextFieldState(),
        onCancelSearchClick = {},
        isInitiallyFocused = true,
        tabMenuContent = {
            Text("Tab menu content")
        }
    )
}

