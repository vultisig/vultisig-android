package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.securedAssetChain
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.screens.select.AssetItem
import com.vultisig.wallet.ui.screens.v2.home.components.rememberIsReduceMotionEnabled
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

private val ChipSize = 54.dp
private val ChipIconSize = 28.dp
private val ChipItemWidth = 70.dp
private val ChipSpacing = 8.dp
private val ChipLabelSpacing = 6.dp
private val ChipSelectedBorderWidth = 1.5.dp
private val ChipBorderWidth = 1.dp
private val Gutter = 16.dp
private val SectionTitleTopPadding = 4.dp
private const val ALL_CHAINS_KEY = "all"
private const val PLACEHOLDER_ROW_COUNT = 4
private val PlaceholderLogoSize = 32.dp
private val PlaceholderTitleWidth = 56.dp
private val PlaceholderAmountWidth = 72.dp
private val PlaceholderValueWidth = 48.dp

/**
 * The inline Send asset step: search, the chain chips and the vault's coins. A search spans every
 * chain; picking a chip clears it. Tapping a coin selects it and moves on.
 */
@Composable
internal fun SendAssetStep(
    state: SendFormUiModel,
    searchFieldState: TextFieldState,
    contentPadding: PaddingValues,
    onSelectChainFilter: (Chain?) -> Unit,
    onSelectAsset: (Coin) -> Unit,
    onAddCustomToken: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Taps outside the search field drop the keyboard; rows and chips do it on their own.
    val dismissKeyboard =
        Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().testTag("SendFormScreen.tokenSelector"),
    ) {
        item(key = "search", contentType = ContentType.SEARCH) {
            VsSearchTextField(fieldState = searchFieldState)
        }

        item(key = "chainTitle", contentType = ContentType.TITLE) {
            SectionTitle(stringResource(R.string.select_chain_chain_title), dismissKeyboard)
        }

        item(key = "chains", contentType = ContentType.CHAINS) {
            SendChainCarousel(
                chains = state.assetChains,
                selected = state.assetChainFilter,
                onSelect = { chain ->
                    focusManager.clearFocus()
                    onSelectChainFilter(chain)
                },
            )
        }

        item(key = "assetTitle", contentType = ContentType.TITLE) {
            SectionTitle(stringResource(R.string.form_token_selection_asset), dismissKeyboard)
        }

        val rows = state.assetRows
        if (!state.isAssetListLoaded) {
            items(
                PLACEHOLDER_ROW_COUNT,
                key = { "placeholder$it" },
                contentType = { ContentType.PLACEHOLDER },
            ) { index ->
                AssetRowPlaceholder(
                    isFirst = index == 0,
                    isLast = index == PLACEHOLDER_ROW_COUNT - 1,
                )
            }
        } else if (rows.isEmpty()) {
            item(key = "empty", contentType = ContentType.EMPTY) {
                EmptyAssetList(
                    canAddCustomToken = state.canAddCustomToken,
                    onAddCustomToken = onAddCustomToken,
                    modifier = dismissKeyboard,
                )
            }
        } else {
            val selectedId = state.selectedCoin?.model?.account?.token?.id
            itemsIndexed(
                rows,
                key = { _, row -> row.model.account.token.id },
                contentType = { _, _ -> ContentType.ROW },
            ) { index, row ->
                val token = row.model.account.token
                val isSecured = token.isSecuredAsset()
                GroupedRow(isFirst = index == 0, isLast = index == rows.lastIndex) { background ->
                    AssetItem(
                        logo = row.tokenLogo,
                        title = row.title,
                        subtitle = token.chain.displayName,
                        amount = row.balance.orEmpty(),
                        value = row.fiatValue.orEmpty(),
                        isDisabled = false,
                        isSecuredAsset = isSecured,
                        sourceChain = token.securedAssetChain().takeIf { isSecured },
                        modifier =
                            Modifier.semantics { selected = token.id == selectedId }
                                .clickable(role = Role.Button) {
                                    focusManager.clearFocus()
                                    onSelectAsset(token)
                                }
                                .then(background),
                    )
                }
            }
        }

        item(key = "bottomInsets", contentType = ContentType.INSETS) {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(StepBottomInsets))
        }
    }
}

/**
 * One row of the asset card: [content] receives the background that rounds the card's first and
 * last corners, and a divider follows every row but the last.
 */
@Composable
private fun GroupedRow(
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable (background: Modifier) -> Unit,
) {
    val corner = Theme.v2.radius.md.size
    Column(modifier = Modifier.padding(horizontal = Gutter)) {
        content(
            Modifier.background(
                color = Theme.v2.colors.backgrounds.secondary,
                shape =
                    RoundedCornerShape(
                        topStart = if (isFirst) corner else 0.dp,
                        topEnd = if (isFirst) corner else 0.dp,
                        bottomStart = if (isLast) corner else 0.dp,
                        bottomEnd = if (isLast) corner else 0.dp,
                    ),
            )
        )
        if (!isLast) {
            UiGradientDivider(
                initialColor = Theme.v2.colors.backgrounds.secondary,
                endColor = Theme.v2.colors.backgrounds.secondary,
            )
        }
    }
}

/** Stands in for an asset row, at the row's size, until the vault's coins arrive. */
@Composable
private fun AssetRowPlaceholder(isFirst: Boolean, isLast: Boolean) {
    GroupedRow(isFirst = isFirst, isLast = isLast) { background ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .then(background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            UiPlaceholderLoader(modifier = Modifier.size(PlaceholderLogoSize).clip(CircleShape))
            UiPlaceholderLoader(modifier = Modifier.width(PlaceholderTitleWidth))
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                UiPlaceholderLoader(modifier = Modifier.width(PlaceholderAmountWidth))
                UiPlaceholderLoader(modifier = Modifier.width(PlaceholderValueWidth).height(12.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.tertiary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter)
                .padding(top = SectionTitleTopPadding, bottom = ChipSpacing),
    )
}

@Composable
private fun EmptyAssetList(
    canAddCustomToken: Boolean,
    onAddCustomToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(horizontal = Gutter),
    ) {
        NoFoundContent(
            message = stringResource(R.string.select_asset_no_token_found),
            icon = R.drawable.circle_dashed,
        )
        if (canAddCustomToken) {
            Text(
                text = stringResource(R.string.select_asset_add_custom_token),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.info,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(onClick = onAddCustomToken)
                        .padding(vertical = 12.dp),
            )
        }
    }
}

/** Left-aligned row of chain chips, "All chains" first; picking one scrolls it to the centre. */
@Composable
private fun SendChainCarousel(chains: List<Chain>, selected: Chain?, onSelect: (Chain?) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isReduceMotion = rememberIsReduceMotionEnabled()
    val items: List<Chain?> = listOf(null) + chains

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        contentPadding = PaddingValues(horizontal = Gutter),
        modifier = Modifier.fillMaxWidth().testTag("SendFormScreen.chainSelector"),
    ) {
        itemsIndexed(items, key = { _, chain -> chain?.id ?: ALL_CHAINS_KEY }) { index, chain ->
            ChainChip(
                chain = chain,
                isSelected = chain == selected,
                onClick = {
                    onSelect(chain)
                    scope.launch {
                        val info = listState.layoutInfo
                        val itemSize =
                            info.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                        val centred = -(info.viewportSize.width - itemSize) / 2
                        if (isReduceMotion) listState.scrollToItem(index, centred)
                        else listState.animateScrollToItem(index, centred)
                    }
                },
            )
        }
    }
}

@Composable
private fun ChainChip(chain: Chain?, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChipLabelSpacing),
        modifier =
            Modifier.width(ChipItemWidth)
                .semantics { selected = isSelected }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(ChipSize)
                    .background(color = Theme.v2.colors.backgrounds.secondary, shape = CircleShape)
                    .border(
                        width = if (isSelected) ChipSelectedBorderWidth else ChipBorderWidth,
                        color =
                            if (isSelected) Theme.v2.colors.primary.accent3
                            else Theme.v2.colors.border.light,
                        shape = CircleShape,
                    ),
        ) {
            if (chain == null) {
                UiIcon(
                    drawableResId = R.drawable.ic_coins,
                    size = ChipIconSize,
                    tint = Theme.v2.colors.text.primary,
                )
            } else {
                Image(
                    painter = painterResource(chain.logo),
                    contentDescription = null,
                    modifier = Modifier.size(ChipIconSize),
                )
            }
        }
        Text(
            text = chain?.displayName ?: stringResource(R.string.send_all_chains),
            style = Theme.brockmann.supplementary.caption,
            color = if (isSelected) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Lets the list reuse a recycled slot only for an item of the same shape.
private enum class ContentType {
    SEARCH,
    TITLE,
    CHAINS,
    PLACEHOLDER,
    EMPTY,
    ROW,
    INSETS,
}
