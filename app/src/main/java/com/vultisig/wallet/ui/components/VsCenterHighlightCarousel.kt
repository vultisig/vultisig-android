package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.screens.select.NetworkUiModel
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import timber.log.Timber

@Composable
fun VsCenterHighlightCarousel(
    chains: List<NetworkUiModel>,
    selectedChain: Chain,
    onSelectChain: (Chain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val itemWidth = 150.dp
    val itemSpacing = 16.dp
    val screenWidth = configuration.screenWidthDp.dp
    val centerOffset = ((screenWidth - itemWidth) / 2).coerceAtLeast(0.dp)

    val listState = rememberLazyListState()

    // Avoid side effects with scrolling programatically to center
    var isProgrammaticScroll by remember { mutableStateOf(false) }


    // Track the last selected index to detect re-selection
    var lastSelectedIndex by remember { mutableStateOf(-1) }

    // Trigger selection when scrolling stops or user release
    LaunchedEffect(listState, chains) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                Timber.d("VsCenterHighlightCarousel: Scroll state changed, isScrolling = $isScrolling, isProgrammatic = $isProgrammaticScroll")

                if (!isScrolling && !isProgrammaticScroll && chains.isNotEmpty()) {
                    // Calculate which item is in the center after snap
                    val layoutInfo = listState.layoutInfo
                    Timber.d("VsCenterHighlightCarousel: Layout info - visible items count = ${layoutInfo.visibleItemsInfo.size}")

                    if (layoutInfo.visibleItemsInfo.isEmpty()) {
                        return@collect
                    }

                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

                    // Find the item closest to center (left or right)
                    val centerItem = layoutInfo.visibleItemsInfo
                        .filter { it.index >= 0 && it.index < chains.size }
                        .minByOrNull { item ->
                            val itemCenter = item.offset + item.size / 2
                            val distance = abs(itemCenter - viewportCenter)
                            Timber.d("VsCenterHighlightCarousel: Item ${item.index} - center = $itemCenter, distance = $distance")
                            distance
                        }

                    // figure out index and trigger onSelectChain
                    centerItem?.let { item ->
                        val safeIndex = item.index.coerceIn(0, chains.size - 1)
                        Timber.d("VsCenterHighlightCarousel: Center item found - index = ${item.index}, safeIndex = $safeIndex, chains.size = ${chains.size}")

                        if (safeIndex < chains.size) {
                            if (safeIndex != lastSelectedIndex) {
                                val selectedChainInCenter = chains[safeIndex].chain
                                Timber.d("VsCenterHighlightCarousel: Calling onSelectChain for ${selectedChainInCenter.name} (${selectedChainInCenter.id})")
                                lastSelectedIndex = safeIndex
                                onSelectChain(selectedChainInCenter)
                            } else {
                                Timber.d("VsCenterHighlightCarousel: Center unchanged (index=$safeIndex); skipping onSelectChain")
                            }
                        } else {
                            Timber.e("VsCenterHighlightCarousel: Safe index $safeIndex still out of bounds for chains list of size ${chains.size}")
                        }
                    } ?: run {
                        Timber.e("VsCenterHighlightCarousel: No valid center item could be determined")
                    }
                } else if (isProgrammaticScroll && !isScrolling) {
                    isProgrammaticScroll = false
                }
            }
    }

    // check if scroll is needed (i.e element is not center already)
    LaunchedEffect(selectedChain) {
        val targetIndex = chains.indexOfFirst { it.chain.id == selectedChain.id }
        if (targetIndex >= 0) {
            lastSelectedIndex = targetIndex
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val currentCenterItem = layoutInfo.visibleItemsInfo
                .filter { it.index >= 0 && it.index < chains.size }
                .minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }

            if (currentCenterItem?.index != targetIndex) {
                isProgrammaticScroll = true
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = with(density) {
                        -(centerOffset.toPx().toInt())
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Theme.colors.backgrounds.primary)
            .padding(vertical = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.select_chain_title),
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.m.medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = centerOffset
                    ),
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = chains,
                        key = { _, item -> item.chain.id }
                    ) { index, network ->
                        CarouselChainItem(
                            chain = network.chain,
                            logo = network.logo,
                            modifier = Modifier.width(itemWidth)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(itemWidth)
                        .height(50.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .border(
                            width = 2.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Colors.Default.persianBlue200, Colors.Default.persianBlue400)
                            ),
                            shape = RoundedCornerShape(30.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun CarouselChainItem(
    chain: Chain,
    logo: ImageModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Theme.colors.backgrounds.secondary)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TokenLogo(
            errorLogoModifier = Modifier
                .size(32.dp)
                .background(Theme.colors.neutral100),
            logo = logo,
            title = "${chain.name} logo",
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = chain.name,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
        )
    }
}