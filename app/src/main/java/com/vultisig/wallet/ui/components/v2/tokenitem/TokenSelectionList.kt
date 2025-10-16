package com.vultisig.wallet.ui.components.v2.tokenitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun <T> TokenSelectionList(
    items: List<T>,
    mapper: (T) -> TokenSelectionGridUiModel,
    searchTextFieldState: TextFieldState,
    onCheckChange: (Boolean, T) -> Unit,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    V2BottomSheet(
        onDismissRequest = onCancelClick,
        leftAction = {
            VsCircleButton(
                drawableResId = R.drawable.big_close,
                onClick = onCancelClick,
                type = VsCircleButtonType.Tertiary,
                size = VsCircleButtonSize.Small,
            )
        },
        rightAction = {
            VsCircleButton(
                drawableResId = R.drawable.big_tick,
                onClick = onDoneClick,
                size = VsCircleButtonSize.Small,
            )

        }
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxSize(),
        ) {
            UiSpacer(
                size = 24.dp
            )

            Text(
                text = stringResource(R.string.chain_selection_select_chains),
                style = Theme.brockmann.headings.title2,
                color = Theme.colors.neutrals.n100,
            )

            UiSpacer(
                size = 16.dp
            )

            SearchBar(
                state = searchTextFieldState,
                onCancelClick = {},
                isInitiallyFocused = false
            )
            UiSpacer(
                size = 24.dp
            )

            if (items.isEmpty()) {
                NoChainFound()
            } else
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 74.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items) { item ->
                        TokenSelectionGridItem(
                            uiModel = mapper(item),
                            onCheckedChange = {
                                onCheckChange(it, item)
                            }
                        )
                    }
                }
        }
    }

}