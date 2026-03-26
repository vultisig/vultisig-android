package com.vultisig.wallet.ui.components.v2.tab

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.ui.components.v2.animation.slideAndFadeSpec
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar

@Composable
internal fun TabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    isTabMenu: Boolean,
    searchTextFieldState: TextFieldState,
    onCancelSearchClick: () -> Unit,
    isInitiallyFocused: Boolean,
    tabMenuContent: @Composable () -> Unit,
) {
    AnimatedContent(targetState = isTabMenu, transitionSpec = slideAndFadeSpec()) {
        if (it) tabMenuContent()
        else {
            SearchBar(
                modifier = modifier.fillMaxWidth(),
                state = searchTextFieldState,
                isInitiallyFocused = isInitiallyFocused,
                onCancelClick = onCancelSearchClick,
            )
        }
    }
}

@Preview
@Composable
private fun TabMenuAndSearchBarPreview() {
    TabMenuAndSearchBar(
        isTabMenu = false,
        searchTextFieldState = rememberTextFieldState(),
        onCancelSearchClick = {},
        isInitiallyFocused = true,
        tabMenuContent = { Text("Tab menu content") },
    )
}
