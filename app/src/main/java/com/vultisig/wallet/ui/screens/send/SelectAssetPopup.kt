package com.vultisig.wallet.ui.screens.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.components.v2.fastselection.components.SelectPopup
import com.vultisig.wallet.ui.components.v2.fastselection.SelectPopupUiModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.select.AssetUiModel
import com.vultisig.wallet.ui.screens.select.SelectNetworkPopupSharedViewModel

@Composable
fun SelectAssetPopup(
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
) {
    val args = backStackEntry.toRoute<Route.Send.SelectAssetPopup>()
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry<Route.Send>()
    }
    val sharedViewModel: SelectNetworkPopupSharedViewModel = hiltViewModel(parentEntry)
    val selectNetworkUiModel = sharedViewModel.uiState.collectAsState().value
    val initialIndex = remember(selectNetworkUiModel.assets) {
        selectNetworkUiModel.assets.indexOfFirst { it.token.id == args.selectedAssetId }
            .takeIf { it >= 0 } ?: 0
    }
    LaunchedEffect(args) {
        sharedViewModel.initAssets(args)
    }
    SelectPopup(
        uiModel = SelectPopupUiModel(
            items = selectNetworkUiModel.assets,
            initialIndex = initialIndex,
            isLongPressActive = selectNetworkUiModel.isLongPressActive,
            currentDragPosition = selectNetworkUiModel.currentDragPosition,
            pressPosition = Offset(args.pressX, args.pressY)
        ),
        onItemSelected = { it: AssetUiModel ->
            sharedViewModel.onAssetSelected(it)
        },
        itemContent = { item, distanceFromCenter ->
            AssetSelectorPickerItem(
                item = item,
                distanceFromCenter = distanceFromCenter,
            )
        },
    )
}