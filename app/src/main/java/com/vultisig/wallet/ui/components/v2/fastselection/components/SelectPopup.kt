package com.vultisig.wallet.ui.components.v2.fastselection.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vultisig.wallet.ui.components.v2.fastselection.SelectPopupUiModel
import kotlin.math.min

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun <T> SelectPopup(
    uiModel: SelectPopupUiModel<T>,
    onItemSelected: (T) -> Unit,
    itemContent: @Composable (T, distanceFromCenter: Int) -> Unit,
) {
    val visibleItems = 7
    var currentSelectionIndex by remember { mutableIntStateOf(uiModel.initialIndex) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeight by remember { mutableIntStateOf(0) }
    var lastKnownY by remember { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // max per-item drag allowed (10.dp)
    val maxPerItemPx = with(density) { 10.dp.toPx() }
    // pad inside modal
    val padPx = with(density) { 10.dp.toPx() }

    var shouldClose by remember { mutableStateOf(false) }

    LaunchedEffect(uiModel.isLongPressActive) {
        if (!uiModel.isLongPressActive && !shouldClose) {
            shouldClose = true
            if (uiModel.items.isNotEmpty() &&
                currentSelectionIndex in uiModel.items.indices
            ) {
                onItemSelected(uiModel.items[currentSelectionIndex])
            }
        }
    }

    LaunchedEffect(
        uiModel.currentDragPosition,
        measuredItemHeight,
        uiModel.isLongPressActive,
        uiModel.items,
    ) {
        if (!uiModel.isLongPressActive) {
            lastKnownY = null
            accumulatedDragY = 0f
            return@LaunchedEffect
        }
        val pos = uiModel.currentDragPosition ?: return@LaunchedEffect
        val currentY = pos.y

        val itemsCount = uiModel.items.size
        val itemH = measuredItemHeight
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        // compute modal inner height (space available for items)
        val modalHeightPx = if (itemH > 0) itemH * visibleItems else 0
        val innerHeight = (modalHeightPx - 2f * padPx).coerceAtLeast(0f)

        // per-item derived from modal when mapping top->bottom
        val perItemFromModal =
            if (itemsCount > 1 && innerHeight > 0f) innerHeight / (itemsCount - 1) else Float.MAX_VALUE

        // If modal-based per-item is small enough (<= max), use absolute mapping top->bottom.
        // Otherwise (few items / large spacing), use delta mode with sensitivity capped to maxPerItemPx.
        val useAbsoluteModalMapping =
            perItemFromModal.isFinite() && perItemFromModal <= maxPerItemPx && itemsCount > visibleItems

        if (useAbsoluteModalMapping) {
            // absolute mapping: top+pad -> first, bottom-pad -> last
            val rawTop = uiModel.pressPosition.y - modalHeightPx / 2f
            val maxTop = (screenHeightPx - modalHeightPx).coerceAtLeast(0f)
            val modalTop = rawTop.coerceIn(0f, maxTop)

            val relativeY = (currentY - modalTop - padPx).coerceIn(0f, innerHeight)
            val fraction = if (innerHeight > 0f) (relativeY / innerHeight) else 0f
            val target = ((fraction * (itemsCount - 1)) + 0.5f).toInt().coerceIn(0, itemsCount - 1)

            if (target != currentSelectionIndex) {
                currentSelectionIndex = target
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            // reset delta anchors
            accumulatedDragY = 0f
            lastKnownY = currentY
        } else {
            // delta-based mode. Sensitivity per-item = min(measured item height, maxPerItemPx)
            val baseSensitivity = if (itemH > 0) itemH.toFloat() else maxPerItemPx
            val sensitivityPx = min(baseSensitivity, maxPerItemPx)

            if (lastKnownY == null) {
                lastKnownY = currentY
                return@LaunchedEffect
            }

            val dy = currentY - lastKnownY!!
            if (dy != 0f) {
                accumulatedDragY += dy
                val indexChange = (accumulatedDragY / sensitivityPx).toInt()
                if (indexChange != 0) {
                    val newIndex = (currentSelectionIndex - indexChange).coerceIn(
                        0,
                        (itemsCount.coerceAtLeast(1) - 1)
                    )
                    if (newIndex != currentSelectionIndex) {
                        currentSelectionIndex = newIndex
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    accumulatedDragY %= sensitivityPx
                }
            }
            lastKnownY = currentY
        }
    }



    Dialog(
        onDismissRequest = { /* prevent dismissal while gesture active */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FastSelectionModalContent(
                modifier = Modifier
                    .align(alignment = Alignment.Center),
                items = uiModel.items,
                currentIndex = currentSelectionIndex,
                pressPosition = uiModel.pressPosition,
                visibleItemCount = visibleItems,
                itemContent = { item, distanceFromCenter ->
                    itemContent(item, distanceFromCenter)
                },
                onItemHeightMeasured = { height ->
                    if (measuredItemHeight == 0) measuredItemHeight = height
                }
            )
        }
    }
}