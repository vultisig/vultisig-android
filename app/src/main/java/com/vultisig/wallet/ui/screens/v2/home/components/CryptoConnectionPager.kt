package com.vultisig.wallet.ui.screens.v2.home.components

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vultisig.wallet.data.models.CryptoConnectionType
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** iOS `HomeTabPager.tabSwitchAnimation` (response 0.5); stiffness is (2π / response)². */
private val TabSwitchSpring: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 158f)

/** Whether the system "Remove animations" accessibility setting is on. */
@Composable
internal fun rememberIsReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
internal fun rememberCryptoConnectionPagerState(
    types: List<CryptoConnectionType>,
    selectedType: CryptoConnectionType,
): PagerState =
    rememberPagerState(initialPage = types.indexOf(selectedType).coerceAtLeast(0)) { types.size }

/** Where [state] sits as a fractional page index, for the bottom navigator's pill to follow. */
@Composable
internal fun rememberPagerIndicatorPosition(state: PagerState): () -> Float =
    remember(state) { { state.currentPage + state.currentPageOffsetFraction } }

@Composable
internal fun rememberCryptoConnectionTabClick(
    state: PagerState,
    types: List<CryptoConnectionType>,
): (CryptoConnectionType) -> Unit {
    val scope = rememberCoroutineScope()
    val isReduceMotion = rememberIsReduceMotionEnabled()
    return remember(state, types, isReduceMotion) {
        { type ->
            val page = types.indexOf(type)
            if (page >= 0) {
                scope.launch {
                    if (isReduceMotion) state.scrollToPage(page)
                    else state.animateScrollToPage(page, animationSpec = TabSwitchSpring)
                }
            }
        }
    }
}

/**
 * [onSettle] fires only for a page other than the one the pager opened on. [keepsOtherPageComposed]
 * keeps the off-screen page alive, hidden from accessibility, so it never reloads when swiped back.
 */
@Composable
internal fun CryptoConnectionPager(
    state: PagerState,
    types: List<CryptoConnectionType>,
    selectedType: CryptoConnectionType,
    onSettle: (CryptoConnectionType) -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    keepsOtherPageComposed: Boolean = false,
    page: @Composable (CryptoConnectionType) -> Unit,
) {
    val currentOnSettle by rememberUpdatedState(onSettle)

    LaunchedEffect(state, types) {
        snapshotFlow { state.settledPage }.drop(1).collect { currentOnSettle(types[it]) }
    }

    LaunchedEffect(state, types, selectedType) {
        val page = types.indexOf(selectedType)
        if (page >= 0 && page != state.settledPage && !state.isScrollInProgress) {
            state.scrollToPage(page)
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        key = { types[it] },
        beyondViewportPageCount = if (keepsOtherPageComposed) types.lastIndex else 0,
    ) { index ->
        val isOffScreen = index != state.settledPage
        Box(modifier = if (isOffScreen) Modifier.clearAndSetSemantics {} else Modifier) {
            page(types[index])
        }
    }
}
