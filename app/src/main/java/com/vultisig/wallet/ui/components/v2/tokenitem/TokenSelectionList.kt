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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel.*
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel.*
import com.vultisig.wallet.ui.theme.Theme

internal data class TokenSelectionGroupUiModel<T>(
    val title: String? = null,
    val items: List<GridTokenUiModel<T>>,
    val mapper: (GridTokenUiModel<T>) -> TokenSelectionGridUiModel,
    val plusUiModel: GridPlusUiModel? = null,
)

internal sealed interface GridTokenUiModel<T> {
    data class SingleToken<T>(val data: T) : GridTokenUiModel<T>
    data class PairToken<T>(val data: Pair<T, T>) : GridTokenUiModel<T>
}


@Composable
internal fun <T> TokenSelectionList(
    items: List<GridTokenUiModel<T>>,
    mapper: (GridTokenUiModel<T>) -> TokenSelectionGridUiModel,
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
                mapper = mapper,
                plusUiModel = plusUiModel,
                title = null,
            )
        ),
        searchTextFieldState = searchTextFieldState,
        titleContent = titleContent,
        notFoundContent = notFoundContent,
        onCheckChange = { isSelected, uiModel ->
            when (uiModel) {
                is PairToken<T> -> error("can not occurs")
                is SingleToken<T> -> {
                    onCheckChange(
                        isSelected,
                        uiModel.data
                    )
                }
            }
        },
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
    onCheckChange: (Boolean, GridTokenUiModel<T>) -> Unit,
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
            modifier = Modifier
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


@Preview
@Composable
private fun TokenSelectionListPreview() {
    TokenSelectionList(
        groups = listOf(
            TokenSelectionGroupUiModel(
                title = "group 1",
                items = listOf(
                    SingleToken(
                        data = Coins.Base.DAI
                    ),
                    PairToken(
                        data = Coins.Ethereum.DAI to Coins.ThorChain.FUZN
                    )
                ),
                mapper = {
                    val tokenSelectionUiModel = when (it) {
                        is PairToken<Coin> -> {
                            TokenUiPair(
                                left = TokenUiSingle(
                                    name = it.data.first.ticker,
                                    logo = getCoinLogo(it.data.first.logo)
                                ),
                                right = TokenUiSingle(
                                    name = it.data.second.ticker,
                                    logo = getCoinLogo(it.data.second.logo)
                                ),
                            )
                        }

                        is SingleToken<Coin> -> {
                            TokenUiSingle(
                                name = it.data.ticker,
                                logo = getCoinLogo(it.data.logo)
                            )
                        }
                    }
                    TokenSelectionGridUiModel(
                        isChecked = true,
                        tokenSelectionUiModel = tokenSelectionUiModel
                    )
                },
                plusUiModel = null,
            ),
        ),
        searchTextFieldState = TextFieldState(),
        titleContent = {
            Column {
                Text(
                    "description",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.extraLight
                )
            }
        },
        notFoundContent = {},
        onCheckChange = { isSelected, uiModel ->
            when (uiModel) {
                is PairToken<Coin> -> {
                    val (firstToken, secondToken) = uiModel.data
                    println("$firstToken $secondToken $isSelected")
                }

                is SingleToken<Coin> -> {
                    val token = uiModel.data
                    println(token)
                }
            }

        },
        onDoneClick = {},
        onCancelClick = {},
        onSetSearchText = {}
    )
}


@Preview
@Composable
private fun TokenSelectionListPreview2() {
    TokenSelectionList(
        items = listOf(
            SingleToken(
                data = Coins.Base.DAI
            ),
        ),
        mapper = {
            when (it) {
                is PairToken<Coin> -> error("can not occurs")
                is SingleToken<Coin> -> TokenSelectionGridUiModel(
                    isChecked = true,
                    tokenSelectionUiModel = TokenUiSingle(
                        name = it.data.ticker,
                        logo = getCoinLogo(it.data.logo)
                    )
                )
            }
        },
        searchTextFieldState = TextFieldState(),
        titleContent = {
            Column {
                Text(
                    "description",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.extraLight
                )
            }
        },
        notFoundContent = {},
        onCheckChange = { _, _ -> },
        onDoneClick = { },
        onCancelClick = { },
        plusUiModel = GridPlusUiModel(
            title = "Custom",
            onClick = {},
        ),
        onSetSearchText = {},
    )
}
