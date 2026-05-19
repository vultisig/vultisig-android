package com.vultisig.wallet.ui.components.v2.fastselection.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.components.v2.fastselection.FastSelectionPopupSharedViewModel
import com.vultisig.wallet.ui.components.v2.fastselection.SelectPopupUiModel
import com.vultisig.wallet.ui.navigation.Route

/**
 * Dialog destination for the Send-form fast token picker. Mirrors [SelectChainPopup]: shares the
 * parent route's [FastSelectionPopupSharedViewModel] so drag gestures dispatched from the Send form
 * drive the popup, and responds to the awaiting caller via `RequestResultRepository`.
 *
 * @param backStackEntry The dialog's back-stack entry; used to read [Route.SelectAssetPopup] args.
 * @param navController Used to look up the parent's back-stack entry so the shared VM is reused.
 */
@Composable
internal inline fun <reified T : Any> SelectAssetPopup(
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
) {
    val args = backStackEntry.toRoute<Route.SelectAssetPopup>()
    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry<T>() }
    val sharedViewModel: FastSelectionPopupSharedViewModel = hiltViewModel(parentEntry)
    val selectAssetUiModel = sharedViewModel.uiState.collectAsState().value
    val initialIndex =
        remember(selectAssetUiModel.assets, key2 = args.selectedAssetId) {
            selectAssetUiModel.assets
                .indexOfFirst { it.token.id == args.selectedAssetId }
                .takeIf { it >= 0 } ?: 0
        }
    LaunchedEffect(args) { sharedViewModel.initAssets(args) }
    SelectPopup(
        uiModel =
            SelectPopupUiModel(
                items = selectAssetUiModel.assets,
                initialIndex = initialIndex,
                isLongPressActive = selectAssetUiModel.isLongPressActive,
                currentDragPosition = selectAssetUiModel.currentDragPosition,
                pressPosition = Offset(args.pressX, args.pressY),
            ),
        key = { it.token.id },
        onItemSelected = sharedViewModel::onAssetSelected,
        itemContent = { item, distanceFromCenter ->
            AssetSelectorPickerItem(item = item, distanceFromCenter = distanceFromCenter)
        },
    )
}
