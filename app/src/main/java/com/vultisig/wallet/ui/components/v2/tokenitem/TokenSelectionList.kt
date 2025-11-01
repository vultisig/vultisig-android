package com.vultisig.wallet.ui.components.v2.tokenitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.theme.Theme

internal data class TokenSelectionGroupUiModel<T>(
    val title: String? = null,
    val items: List<T>,
    val mapper: (T) -> TokenSelectionGridUiModel,
    val plusUiModel: GridPlusUiModel? = null,
)


@Composable
internal fun <T> TokenSelectionList(
    items: List<T>,
    mapper: (T) -> TokenSelectionGridUiModel,
    searchTextFieldState: TextFieldState,
    titleContent: @Composable () -> Unit,
    notFoundContent: @Composable () -> Unit,
    onCheckChange: (Boolean, T) -> Unit,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit,
    plusUiModel: GridPlusUiModel? = null,
    onSetSearchText: (String) -> Unit = {},
) {
    TokenSelectionList(
        groups = listOf(
            TokenSelectionGroupUiModel(
                items = items,
                plusUiModel = plusUiModel,
                mapper = mapper
            )
        ),
        searchTextFieldState = searchTextFieldState,
        titleContent = titleContent,
        notFoundContent = notFoundContent,
        onCheckChange = onCheckChange,
        onDoneClick = onDoneClick,
        onCancelClick = onCancelClick,
        onSetSearchText = onSetSearchText
    )
}

@Composable
internal fun <T> TokenSelectionList(
    groups: List<TokenSelectionGroupUiModel<T>>,
    searchTextFieldState: TextFieldState,
    titleContent: @Composable () -> Unit,
    notFoundContent: @Composable () -> Unit,
    onCheckChange: (Boolean, T) -> Unit,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSetSearchText: (String) -> Unit = {},
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

            titleContent()

            UiSpacer(
                size = 16.dp
            )

            SearchBar(
                state = searchTextFieldState,
                onCancelClick = {},
                isInitiallyFocused = false,
                isPasteEnabled = true,
                onSetSearchText = onSetSearchText,
            )
            UiSpacer(
                size = 24.dp
            )

            if (groups.isEmpty()) {
                notFoundContent()
            } else
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 74.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    groups.forEach { (title, items, mapper, plusUiModel) ->
                        title?.let {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                GridTitle(it)
                            }
                        }

                        plusUiModel?.let {
                            item {
                                GridPlus(
                                    model = it
                                )
                            }
                        }

                        items(items) { item ->
                            GridItem(
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

}

@Composable
private fun GridTitle(title: String) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.primary,
        modifier = Modifier
            .widthIn(max = 74.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
