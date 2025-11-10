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
import com.vultisig.wallet.ui.screens.select.NetworkUiModel
import com.vultisig.wallet.ui.screens.select.SelectNetworkPopupSharedViewModel

@Composable
fun SelectChainPopup(
    backStackEntry: NavBackStackEntry,
    navController: NavHostController,
) {
    val args = backStackEntry.toRoute<Route.Send.SelectNetworkPopup>()
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry<Route.Send>()
    }
    val sharedViewModel: SelectNetworkPopupSharedViewModel = hiltViewModel(parentEntry)
    val selectNetworkUiModel = sharedViewModel.uiState.collectAsState().value
    val initialIndex = remember(selectNetworkUiModel.networks, key2 = args.selectedNetworkId) {
        selectNetworkUiModel.networks.indexOfFirst { it.chain.id == args.selectedNetworkId }
            .takeIf { it >= 0 } ?: 0
    }
    LaunchedEffect(args) {
        sharedViewModel.initNetworks(args)
    }
    SelectPopup(
        uiModel = SelectPopupUiModel(
            items = selectNetworkUiModel.networks,
            initialIndex = initialIndex,
            isLongPressActive = selectNetworkUiModel.isLongPressActive,
            currentDragPosition = selectNetworkUiModel.currentDragPosition,
            pressPosition = Offset(args.pressX, args.pressY)
        ),
        onItemSelected = { it: NetworkUiModel ->
            sharedViewModel.onNetworkSelected(it)
        },
        itemContent = { item, distanceFromCenter ->
            ChainSelectorPickerItem(
                item = item,
                distanceFromCenter = distanceFromCenter,
            )
        },
    )
}