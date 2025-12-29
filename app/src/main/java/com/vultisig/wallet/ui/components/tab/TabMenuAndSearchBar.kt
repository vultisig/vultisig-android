package com.vultisig.wallet.ui.components.tab

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.components.animation.slideAndFadeSpec
import com.vultisig.wallet.ui.components.searchbar.VsSearchBar

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
        transitionSpec = slideAndFadeSpec(),
    ) {
        if (it)
            tabMenuContent()
        else {
            VsSearchBar(
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

