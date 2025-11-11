package com.vultisig.wallet.ui.components.v2.fastselection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import com.vultisig.wallet.ui.components.v2.fastselection.components.SelectChainPopup
import com.vultisig.wallet.ui.navigation.Route

internal inline fun <reified T : Any, reified ParentRoute : Any> NavGraphBuilder.contentWithFastSelection(
    navController: NavHostController,
    crossinline content: @Composable (
        onDragStart: (Offset) -> Unit,
        onDrag: (Offset) -> Unit,
        onDragEnd: () -> Unit,
    ) -> Unit
) {
    composable<T> { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry<ParentRoute>()
        }
        val sharedViewModel: FastSelectionPopupSharedViewModel = hiltViewModel(parentEntry)

        content(
            sharedViewModel::onDragStart,
            sharedViewModel::onDrag,
            sharedViewModel::onDragEnd
        )
    }

    dialog<Route.SelectNetworkPopup> { backStackEntry ->
        SelectChainPopup<ParentRoute>(
            backStackEntry = backStackEntry,
            navController = navController
        )
    }
}