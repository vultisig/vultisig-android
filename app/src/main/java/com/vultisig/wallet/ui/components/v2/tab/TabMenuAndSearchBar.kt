package com.vultisig.wallet.ui.components.v2.tab

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.animation.slideAndFadeSpec
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.theme.Theme

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.fillMaxWidth(),
            ) {
                SearchBar(
                    modifier = Modifier.weight(1f),
                    state = searchTextFieldState,
                    isInitiallyFocused = isInitiallyFocused,
                )
                UiSpacer(size = 8.dp)
                V2Container(
                    type = ContainerType.SECONDARY,
                    cornerType = CornerType.Circular,
                    modifier =
                        Modifier.clickOnce(
                            onClick = {
                                searchTextFieldState.clearText()
                                onCancelSearchClick()
                            }
                        ),
                ) {
                    UiIcon(
                        drawableResId = R.drawable.close_2,
                        size = 16.dp,
                        tint = Theme.v2.colors.text.primary,
                        modifier = Modifier.padding(all = 12.dp),
                    )
                }
            }
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
