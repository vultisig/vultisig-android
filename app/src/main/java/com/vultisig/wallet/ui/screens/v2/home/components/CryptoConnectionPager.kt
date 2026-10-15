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

/**
 * A tab tap's slide: lands a little past the page and settles back. Matches iOS
 * `HomeTabPager.tabSwitchAnimation`, `spring(response: 0.5, dampingFraction: 0.72)`; stiffness is
 * (2π / response)².
 */
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

/** A pager over [types], opened on [selectedType]. */
@Composable
internal fun rememberCryptoConnectionPagerState(
    types: List<CryptoConnectionType>,
    selectedType: CryptoConnectionType,
): PagerState =
    rememberPagerState(initialPage = types.indexOf(selectedType).coerceAtLeast(0)) { types.size }

/**
 * What a tap on a bottom-navigator tab does: slides the pager there the way a swipe would, or jumps
 * there when the system has animations removed.
 */
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
 * The Wallet and DeFi pages side by side: a drag follows the finger, a flick switches, and the page
 * the pager comes to rest on, once it differs from the one it opened on, is reported through
 * [onSettle]. When [selectedType] moves while the pager is idle (another screen switched the shared
 * toggle) the pager jumps there.
 *
 * [keepsOtherPageComposed] holds the off-screen page alive, so it neither rebuilds nor reloads when
 * swiped back to; it is then hidden from accessibility. Without it only the page on screen exists.
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
