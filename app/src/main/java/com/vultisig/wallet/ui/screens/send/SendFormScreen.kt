@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.screens.send

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.CommonViewModel
import com.vultisig.wallet.app.activity.RouteB
import com.vultisig.wallet.app.activity.RouteBDialog
import com.vultisig.wallet.app.activity.SelectableItem
import com.vultisig.wallet.app.activity.XXUiModel
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import com.vultisig.wallet.ui.screens.select.SelectNetworkPopupSharedUiModel
import com.vultisig.wallet.ui.screens.select.SelectNetworkPopupSharedViewModel
import com.vultisig.wallet.ui.screens.select.SelectableNetworkUiModel
import com.vultisig.wallet.ui.screens.swap.TokenChip
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.min





internal fun NavGraphBuilder.sendScreen(
    navController: NavHostController,
) {

    composable<Route.Send.SendMain> { backStackEntry ->

        val viewModel: SendFormViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsState()
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry<Route.Send>()
        }
        val sharedViewModel: SelectNetworkPopupSharedViewModel = hiltViewModel(parentEntry)


        LaunchedEffect(viewModel.vaultId) {
            viewModel.vaultId?.let { vaultId ->
                sharedViewModel.setVaultId(vaultId = vaultId)
            }
        }

        val selectedItemId = sharedViewModel.uiState.collectAsState().value.selectedNetwork.id

        SendFormScreen(
            state = state,
            addressFieldState = viewModel.addressFieldState,
            tokenAmountFieldState = viewModel.tokenAmountFieldState,
            fiatAmountFieldState = viewModel.fiatAmountFieldState,
            memoFieldState = viewModel.memoFieldState,
            onDstAddressLostFocus = { /* no-op */ },
            onTokenAmountLostFocus = viewModel::validateTokenAmount,
            onDismissError = viewModel::dismissError,
            onSelectNetworkRequest = viewModel::selectNetwork,
            onSelectTokenRequest = viewModel::openTokenSelection,
            onSetOutputAddress = viewModel::setOutputAddress,
            onChooseMaxTokenAmount = viewModel::chooseMaxTokenAmount,
            onChoosePercentageAmount = viewModel::choosePercentageAmount,
            onScanDstAddressRequest = viewModel::scanAddress,
            onAddressBookClick = viewModel::openAddressBook,
            onSend = viewModel::send,
            onRefreshRequest = viewModel::refreshGasFee,
            onGasSettingsClick = viewModel::openGasSettings,
            onBackClick = viewModel::back,
            onToogleAmountInputType = viewModel::toggleAmountInputType,
            onExpandSection = viewModel::expandSection,

            onDragStart = sharedViewModel::onDragStart,
            onDrag = sharedViewModel::onDrag,
            onDragEnd = sharedViewModel::resetDrag,
            onDragCancel = sharedViewModel::resetDrag,
            onLongPressStarted = viewModel::openNetworkPopup,
        )

        val selectedChain = state.selectedCoin?.model?.address?.chain
        val specific = state.specific

        if (state.showGasSettings && selectedChain != null && specific != null) {
            GasSettingsScreen(
                chain = selectedChain,
                specific = specific,
                onSaveGasSettings = viewModel::saveGasSettings,
                onDismissGasSettings = viewModel::dismissGasSettings,
            )
        }
    }


    dialog<Route.Send.SelectNetworkPopup> { backStackEntry ->

        val args = backStackEntry.toRoute<Route.Send.SelectNetworkPopup>()

        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry<Route.Send>()
        }
        val viewModel: SendFormViewModel = hiltViewModel()
        val sharedViewModel: SelectNetworkPopupSharedViewModel = hiltViewModel(parentEntry)
        SelectChainPopup(
            navController = navController,
            uiModel = sharedViewModel.uiState.collectAsState().value,
            selectedItemId = args.selectedNetworkId,
            pressPosition = Offset(args.pressX, args.pressY),
            loadData = {
                sharedViewModel.loadData()
            },
            onItemSelected = {
                sharedViewModel.onItemSelected(it)
            }
        )
    }
}


@Composable
private fun SelectChainPopup(
    navController: NavHostController,
    uiModel: SelectNetworkPopupSharedUiModel,
    onItemSelected: (SelectableNetworkUiModel) -> Unit,
    loadData: () -> Unit,
    selectedItemId: String?,
    pressPosition: Offset,
) {
    LaunchedEffect(Unit) {
        loadData()
    }


    val initialIndex = remember(uiModel.networks, selectedItemId) {
        uiModel.networks.indexOfFirst { it.networkUiModel.chain.id == selectedItemId }
            .takeIf { it >= 0 }
            ?: uiModel.networks.indexOfFirst { it.isSelected }.takeIf { it >= 0 } ?: 0
    }

    val visibleItems = 7
    var currentSelectionIndex by remember { mutableIntStateOf(initialIndex) } // apply preselected immediately
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeight by remember { mutableIntStateOf(0) }
    var lastKnownY by remember { mutableStateOf<Float?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // max per-item drag allowed (20.dp)
    val maxPerItemPx = with(density) { 20.dp.toPx() }
    // pad inside modal
    val padPx = with(density) { 10.dp.toPx() }

    var shouldClose by remember { mutableStateOf(false) }

    LaunchedEffect(uiModel.isLongPressActive) {
        if (!uiModel.isLongPressActive && !shouldClose) {
            shouldClose = true
            if (uiModel.networks.isNotEmpty() &&
                currentSelectionIndex in uiModel.networks.indices
            ) {
                onItemSelected(uiModel.networks[currentSelectionIndex])
            }
            navController.popBackStack()
        }
    }

    LaunchedEffect(
        uiModel.currentDragPosition,
        measuredItemHeight,
        uiModel.isLongPressActive,
        uiModel.networks,
    ) {
        if (!uiModel.isLongPressActive) {
            lastKnownY = null
            accumulatedDragY = 0f
            return@LaunchedEffect
        }
        val pos = uiModel.currentDragPosition ?: return@LaunchedEffect
        val currentY = pos.y

        val itemsCount = uiModel.networks.size
        val visibleCount = visibleItems
        val itemH = measuredItemHeight
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        // compute modal inner height (space available for items)
        val modalHeightPx = if (itemH > 0) itemH * visibleCount else 0
        val innerHeight = (modalHeightPx - 2f * padPx).coerceAtLeast(0f)

        // per-item derived from modal when mapping top->bottom
        val perItemFromModal =
            if (itemsCount > 1 && innerHeight > 0f) innerHeight / (itemsCount - 1) else Float.MAX_VALUE

        // If modal-based per-item is small enough (<= max), use absolute mapping top->bottom.
        // Otherwise (few items / large spacing), use delta mode with sensitivity capped to maxPerItemPx.
        val useAbsoluteModalMapping =
            perItemFromModal.isFinite() && perItemFromModal <= maxPerItemPx && itemsCount > visibleCount

        if (useAbsoluteModalMapping) {
            // absolute mapping: top+pad -> first, bottom-pad -> last
            val rawTop = pressPosition.y - modalHeightPx / 2f
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
                items = uiModel.networks,
                currentIndex = currentSelectionIndex,
                pressPosition = pressPosition,
                visibleItemCount = visibleItems,
                itemContent = { item, isCenterItem, distanceFromCenter ->
                    PickerItem(
                        item = item,
                        isCenterItem = isCenterItem,
                        distanceFromCenter = distanceFromCenter
                    )
                },
                onItemHeightMeasured = { height ->
                    if (measuredItemHeight == 0) measuredItemHeight = height
                }
            )
        }
    }
}


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun FastSelectionModalContent(
    items: List<SelectableNetworkUiModel>,
    currentIndex: Int,
    pressPosition: Offset,
    visibleItemCount: Int = 9,
    onItemHeightMeasured: (Int) -> Unit,
    itemContent: @Composable (item: SelectableNetworkUiModel, isCenterItem: Boolean, distanceFromCenter: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var itemHeightPx by remember { mutableIntStateOf(0) }
    var isHeightMeasured by remember { mutableStateOf(false) }

    val modalWidth = with(density) { (configuration.screenWidthDp * 0.85f).dp.toPx() }

    val modalHeight = itemHeightPx * visibleItemCount
    val centerOffset = (modalHeight / 2 - itemHeightPx / 2).toInt()

    val xOffset = if (modalWidth > 0) {
        (pressPosition.x - modalWidth / 2)
            .coerceIn(0f, configuration.screenWidthDp * density.density - modalWidth)
    } else 0f

    val yOffset = if (modalHeight > 0) {
        (pressPosition.y - modalHeight / 2)
            .coerceIn(0f, configuration.screenHeightDp * density.density - modalHeight)
    } else 0f

    LaunchedEffect(currentIndex, isHeightMeasured) {
        if (currentIndex in items.indices && isHeightMeasured) {
            scope.launch {
                val paddingItems = visibleItemCount / 2
                listState.animateScrollToItem(
                    index = currentIndex + paddingItems,
                    scrollOffset = -centerOffset
                )
            }
        }
    }

    LaunchedEffect(isHeightMeasured) {
        if (isHeightMeasured) {
            val paddingItems = visibleItemCount / 2
            listState.scrollToItem(
                index = currentIndex + paddingItems,
                scrollOffset = -centerOffset
            )
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(xOffset.toInt(), yOffset.toInt()) }
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(
                initialScale = 0.8f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(durationMillis = 150)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Box(
                modifier = Modifier
                    .width(with(density) { modalWidth.toDp() })
                    .then(
                        if (isHeightMeasured) {
                            Modifier.height(with(density) { modalHeight.toDp() })
                        } else {
                            Modifier.wrapContentHeight()
                        }
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val paddingItems = visibleItemCount / 2
                    val paddedItems = buildList {
                        repeat(paddingItems) { add(null) }
                        addAll(items)
                        repeat(paddingItems) { add(null) }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(paddedItems) { index, item ->
                            val actualIndex = index - paddingItems

                            if (item != null) {
                                val distanceFromCenter = abs(actualIndex - currentIndex)
                                val isCenterItem = distanceFromCenter == 0

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            if (!isHeightMeasured && coordinates.size.height > 0) {
                                                itemHeightPx = coordinates.size.height
                                                onItemHeightMeasured(coordinates.size.height)
                                                isHeightMeasured = true
                                            }
                                        }
                                ) {
                                    itemContent(item, isCenterItem, distanceFromCenter)
                                }
                            } else {
                                if (isHeightMeasured) {
                                    Spacer(modifier = Modifier.height(with(density) { itemHeightPx.toDp() }))
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    if (isHeightMeasured) {
                        // Center highlight indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { itemHeightPx.toDp() })
                                .align(Alignment.Center)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp)
                                )
                        )

                        // Top and bottom fade gradients
                        val fadeHeight = with(density) { (itemHeightPx * 1.5f).toDp() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fadeHeight)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fadeHeight)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(
    item: SelectableNetworkUiModel,
    isCenterItem: Boolean,
    distanceFromCenter: Int,
    modifier: Modifier = Modifier,
) {
    val scale = when (distanceFromCenter) {
        0 -> 1f
        1 -> 0.95f
        2 -> 0.90f
        else -> 0.85f
    }

    val alpha = when (distanceFromCenter) {
        0 -> 1f
        1 -> 0.7f
        2 -> 0.4f
        else -> 0.2f
    }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )

    Surface(
        modifier = modifier
            .scale(animatedScale)
            .alpha(animatedAlpha),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.networkUiModel.chain.raw.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = item.networkUiModel.chain.raw,
                fontSize = 16.sp,
                fontWeight = if (isCenterItem) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )


            Text(
                text = item.networkUiModel.value.orEmpty(),
                fontSize = 14.sp,
                fontWeight = if (isCenterItem) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }
    }
}

@Composable
private fun SendFormScreen(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    memoFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onSelectNetworkRequest: () -> Unit = {},
    onSelectTokenRequest: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onChooseMaxTokenAmount: () -> Unit = {},
    onChoosePercentageAmount: (Float) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScanDstAddressRequest: () -> Unit = {},
    onSend: () -> Unit = {},
    onRefreshRequest: () -> Unit = {},
    onGasSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToogleAmountInputType: (Boolean) -> Unit = {},
    onExpandSection: (SendSections) -> Unit = {},

    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onLongPressStarted: (Offset) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.send_screen_title),
                onBackClick = onBackClick,
            )
        },
        content = { contentPadding ->
            val pullToRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefreshRequest,
                state = pullToRefreshState,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        color = Theme.colors.primary.accent3,
                        state = pullToRefreshState
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(all = 16.dp)
                ) {
                    // select asset
                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Asset,
                        onToggle = {
                            onExpandSection(SendSections.Asset)
                        },
                        complete = true,
                        title = stringResource(R.string.form_token_selection_asset),
                        completeTitleContent = {
                            Row(
                                modifier = Modifier.weight(1f)
                            ) {
                                val selectedToken = state.selectedCoin

                                TokenLogo(
                                    errorLogoModifier = Modifier
                                        .size(16.dp)
                                        .background(Theme.colors.neutral100),
                                    logo = selectedToken?.tokenLogo ?: "",
                                    title = selectedToken?.title ?: "",
                                    modifier = Modifier
                                        .size(16.dp)
                                )

                                UiSpacer(4.dp)

                                Text(
                                    text = selectedToken?.title ?: "",
                                    style = Theme.brockmann.supplementary.caption,
                                    color = Theme.colors.text.extraLight,
                                )
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            ChainSelector(
                                title = stringResource(R.string.send_from_address),
                                // TODO selectedChain should not be nullable
                                //  or default value should be something else
                                chain = state.selectedCoin?.model?.address?.chain
                                    ?: Chain.ThorChain,
                                onClick = onSelectNetworkRequest,
                                onDragCancel = onDragCancel,
                                onDrag = onDrag,
                                onDragStart = onDragStart,
                                onDragEnd = onDragEnd,
                                onLongPressStarted = onLongPressStarted,
                            )

                            UiSpacer(12.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                            ) {
                                TokenChip(
                                    selectedToken = state.selectedCoin,
                                    onSelectTokenClick = onSelectTokenRequest,
                                )

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f),
                                ) {
                                    state.selectedCoin?.let { token ->
                                        Text(
                                            text = stringResource(
                                                R.string.form_token_selection_balance,
                                                token.balance ?: ""
                                            ),
                                            color = Theme.colors.text.light,
                                            style = Theme.brockmann.body.s.medium,
                                            textAlign = TextAlign.End,
                                        )

                                        UiSpacer(2.dp)

                                        token.fiatValue?.let { fiatValue ->
                                            Text(
                                                text = fiatValue,
                                                textAlign = TextAlign.End,
                                                color = Theme.colors.text.extraLight,
                                                style = Theme.brockmann.supplementary.caption,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // input dst address
                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Address,
                        complete = state.isDstAddressComplete,
                        title = stringResource(R.string.add_address_address_title),
                        onToggle = {
                            onExpandSection(SendSections.Address)
                        },
                        completeTitleContent = {
                            Text(
                                text = addressFieldState.text.toString(),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.body.s.medium,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.send_from_address),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.caption,
                            )

                            UiSpacer(12.dp)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = Theme.colors.borders.light,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .background(
                                        color = Theme.colors.backgrounds.secondary,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = state.srcVaultName,
                                    color = Theme.colors.text.primary,
                                    style = Theme.brockmann.supplementary.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )

                                Text(
                                    text = state.srcAddress,
                                    color = Theme.colors.text.extraLight,
                                    style = Theme.brockmann.supplementary.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }

                            UiSpacer(16.dp)

                            Text(
                                text = stringResource(R.string.send_to_address),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.caption,
                            )

                            UiSpacer(12.dp)

                            VsTextInputField(
                                textFieldState = addressFieldState,
                                hint = stringResource(R.string.send_to_address_hint),
                                onFocusChanged = {
                                    if (!it) {
                                        onDstAddressLostFocus()
                                    }
                                },
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                                innerState = if (state.dstAddressError != null)
                                    VsTextInputFieldInnerState.Error
                                else VsTextInputFieldInnerState.Default,
                                footNote = state.dstAddressError?.asString(),
                                modifier = Modifier
                                    .fillMaxWidth(),
                            )

                            UiSpacer(16.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PasteIcon(
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onPaste = onSetOutputAddress
                                )

                                UiIcon(
                                    drawableResId = R.drawable.camera,
                                    size = 20.dp,
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onClick = onScanDstAddressRequest,
                                )

                                UiIcon(
                                    drawableResId = R.drawable.ic_bookmark,
                                    size = 20.dp,
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onClick = onAddressBookClick,
                                )
                            }
                        }
                    }


                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Amount,
                        onToggle = {
                            if (state.isDstAddressComplete &&
                                addressFieldState.text.isNotEmpty()
                            ) {
                                onExpandSection(SendSections.Amount)
                            }
                        },
                        expandedTitleActions = {
                            if (state.hasGasSettings) {
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .weight(1f),
                                ) {
                                    UiIcon(
                                        drawableResId = R.drawable.advance_gas_settings,
                                        size = 16.dp,
                                        tint = Theme.colors.text.primary,
                                        onClick = onGasSettingsClick,
                                    )
                                }
                            }
                        },
                        title = stringResource(R.string.send_amount)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(211.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(
                                            horizontal = 54.dp,
                                        )
                                        .align(Alignment.Center),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val primaryAmountText: String
                                    val secondaryAmountText: String
                                    val primaryFieldState: TextFieldState
                                    val secondaryFieldState: TextFieldState

                                    if (state.usingTokenAmountInput) {
                                        primaryAmountText = state.selectedCoin?.title ?: ""
                                        secondaryAmountText = state.fiatCurrency
                                        primaryFieldState = tokenAmountFieldState
                                        secondaryFieldState = fiatAmountFieldState
                                    } else {
                                        primaryAmountText = state.fiatCurrency
                                        secondaryAmountText = state.selectedCoin?.title ?: ""
                                        primaryFieldState = fiatAmountFieldState
                                        secondaryFieldState = tokenAmountFieldState
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        BasicTextField(
                                            state = primaryFieldState,
                                            lineLimits = TextFieldLineLimits.MultiLine(
                                                maxHeightInLines = 3,
                                            ),
                                            textStyle = Theme.brockmann.headings.largeTitle
                                                .copy(
                                                    color = Theme.colors.text.primary,
                                                    textAlign = TextAlign.Center,
                                                ),
                                            cursorBrush = Theme.cursorBrush,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Send,
                                            ),
                                            onKeyboardAction = {
                                                focusManager.clearFocus()
                                                onSend()
                                            },
                                            modifier = Modifier
                                                .width(IntrinsicSize.Min),
                                            decorator = { textField ->
                                                if (primaryFieldState.text.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Theme.colors.text.light,
                                                        style = Theme.brockmann.headings.largeTitle,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .wrapContentWidth()
                                                    )
                                                }
                                                textField()
                                            }
                                        )

                                        Text(
                                            text = " $primaryAmountText",
                                            color = Theme.colors.text.primary,
                                            style = Theme.brockmann.headings.largeTitle,
                                            textAlign = TextAlign.Center,
                                        )
                                    }

                                    Text(
                                        text = "${secondaryFieldState.text.ifEmpty { "0" }} $secondaryAmountText",
                                        color = Theme.colors.text.extraLight,
                                        style = Theme.brockmann.body.s.medium,
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                TokenFiatToggle(
                                    isTokenSelected = state.usingTokenAmountInput,
                                    onTokenSelected = {
                                        onToogleAmountInputType(it)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd),
                                )
                            }

                            UiSpacer(12.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PercentageChip(
                                    title = "25%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.25f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = "50%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.5f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = "75%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.75f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = stringResource(R.string.send_screen_max),
                                    isSelected = false,
                                    onClick = onChooseMaxTokenAmount,
                                    modifier = Modifier
                                        .weight(1f),
                                )
                            }

                            UiSpacer(12.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .background(
                                        color = Theme.colors.backgrounds.secondary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(
                                        all = 16.dp,
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.send_form_balance_available),
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.colors.text.primary,
                                )

                                Text(
                                    text = state.selectedCoin?.balance ?: "",
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.colors.text.light,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .weight(1f),
                                )
                            }

                            UiSpacer(12.dp)

                            // memo
                            if (state.hasMemo) {
                                var isMemoExpanded by remember { mutableStateOf(false) }

                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isMemoExpanded) 180f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "caretRotation"
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            isMemoExpanded = !isMemoExpanded
                                        }
                                        .padding(
                                            vertical = 2.dp,
                                        )
                                ) {
                                    Text(
                                        text = stringResource(R.string.send_form_add_memo),
                                        style = Theme.brockmann.supplementary.caption,
                                        color = Theme.colors.text.extraLight,
                                        modifier = Modifier
                                            .weight(1f),
                                    )

                                    UiIcon(
                                        drawableResId = R.drawable.ic_caret_down,
                                        tint = Theme.colors.text.primary,
                                        size = 16.dp,
                                        modifier = Modifier
                                            .rotate(rotationAngle)
                                    )
                                }

                                UiSpacer(12.dp)

                                AnimatedVisibility(
                                    visible = isMemoExpanded,
                                ) {
                                    val clipboardData = VsClipboardService.getClipboardData()
                                    VsTextInputField(
                                        textFieldState = memoFieldState,
                                        hint = stringResource(R.string.send_form_enter_memo),
                                        trailingIcon = R.drawable.paste,
                                        onTrailingIconClick = {
                                            clipboardData.value
                                                ?.takeIf { it.isNotEmpty() }
                                                ?.let {
                                                    memoFieldState.setTextAndPlaceCursorAtEnd(
                                                        text = it
                                                    )
                                                }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                    )

                                    UiSpacer(12.dp)
                                }
                            }

                            if (state.showGasFee) {
                                FadingHorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                )

                                UiSpacer(12.dp)

                                EstimatedNetworkFee(
                                    tokenGas = state.totalGas.asString(),
                                    fiatGas = state.estimatedFee.asString(),
                                )
                            }
                        }
                    }

                    UiSpacer(24.dp)

                    AnimatedContent(
                        targetState = state.reapingError,
                        label = "error message"
                    ) { errorMessage ->
                        if (errorMessage != null) {
                            Column {
                                UiSpacer(size = 8.dp)
                                Text(
                                    text = errorMessage.asString(),
                                    color = Theme.colors.error,
                                    style = Theme.menlo.body1
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (state.isLoading)
                    VsButtonState.Disabled
                else
                    VsButtonState.Enabled,
                onClick = {
                    if (!state.isLoading) {
                        focusManager.clearFocus()
                        onSend()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
            )
        }
    )
}

@Composable
internal fun EstimatedNetworkFee(
    tokenGas: String,
    fiatGas: String,
    isLoading: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.send_form_est_network_fee),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f),
        ) {
            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )

                UiSpacer(6.dp)

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )
            } else {
                Text(
                    text = tokenGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                Text(
                    text = fiatGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                )
            }
        }
    }
}

@Composable
internal fun FadingHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Theme.colors.backgrounds.secondary.copy(alpha = 0f),
                        Color(0xFF284570),
                        Theme.colors.backgrounds.secondary.copy(alpha = 0f),
                    ),
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY,
                    tileMode = TileMode.Clamp
                )
            )
    )
}

@Composable
private fun Modifier.vsClickableBackground() =
    border(
        border = BorderStroke(
            width = 1.dp,
            color = Theme.colors.borders.light,
        ),
        shape = RoundedCornerShape(12.dp),
    )
        .background(
            color = Theme.colors.backgrounds.secondary,
            shape = RoundedCornerShape(12.dp),
        )

@Composable
private fun PercentageChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.light,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.colors.primary.accent3,
                        shape = RoundedCornerShape(99.dp),
                    )
                else
                    Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = RoundedCornerShape(99.dp),
                    )
            )
            .padding(
                all = 4.dp,
            )
    )
}

@Composable
private fun TokenFiatToggle(
    isTokenSelected: Boolean,
    onTokenSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(99.dp)
            )
            .padding(
                all = 4.dp,
            )
    ) {
        ToggleButton(
            drawableResId = R.drawable.ic_coins,
            isSelected = isTokenSelected,
            onClick = { onTokenSelected(true) },
        )

        ToggleButton(
            drawableResId = R.drawable.ic_dollar_sign,
            isSelected = !isTokenSelected,
            onClick = { onTokenSelected(false) },
        )
    }
}

@Composable
private fun ToggleButton(
    @DrawableRes drawableResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    UiIcon(
        drawableResId = drawableResId,
        size = 16.dp,
        tint = Theme.colors.text.light,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.colors.primary.accent3,
                        shape = CircleShape,
                    )
                else Modifier
            )
            .padding(all = 8.dp)
    )
}


@Composable
private fun FoldableSection(
    expanded: Boolean = false,
    complete: Boolean = false,

    completeTitleContent: (@Composable RowScope.() -> Unit)? = null,
    expandedTitleActions: (@Composable RowScope.() -> Unit)? = null,

    onToggle: () -> Unit = {},

    title: String,

    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.colors.borders.normal,
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(all = 16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = title,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )

            if (expanded) {
                expandedTitleActions?.invoke(this)
            } else {
                if (complete) {
                    completeTitleContent?.invoke(this)

                    UiIcon(
                        drawableResId = R.drawable.ic_check,
                        size = 16.dp,
                        tint = Theme.colors.alerts.success,
                    )

                    UiSpacer(1.dp)

                    UiIcon(
                        drawableResId = R.drawable.pencil,
                        size = 16.dp,
                        tint = Theme.colors.text.primary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
        ) {
            FadingHorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                    ),
            )

            content()
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendFormScreen(
        state = SendFormUiModel(
            totalGas = UiText.DynamicString("12.5 Eth"),
            showGasFee = true,
            estimatedFee = UiText.DynamicString("$3.4"),
        ),
        addressFieldState = TextFieldState(),
        tokenAmountFieldState = TextFieldState(),
        fiatAmountFieldState = TextFieldState(),
        memoFieldState = TextFieldState(),
        onDragStart = {},
        onDrag = {},
        onDragEnd = {},
        onDragCancel = {},
        onLongPressStarted = {},
    )
}