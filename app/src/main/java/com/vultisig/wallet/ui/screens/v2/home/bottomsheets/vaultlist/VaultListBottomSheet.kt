package com.vultisig.wallet.ui.screens.v2.home.bottomsheets.vaultlist

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.bottomsheets.navhost.VsBottomSheetNavHost
import com.vultisig.wallet.ui.components.v2.bottomsheets.navhost.rememberVsBottomSheetNavController
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.folder.CreateFolderScreen
import com.vultisig.wallet.ui.screens.folder.FolderScreen
import com.vultisig.wallet.ui.screens.v2.home.bottomsheets.vaultlist.components.VaultListScreen

@Composable
internal fun VaultListBottomSheet(
    vaultList: Route.VaultList,
    onDismiss: () -> Unit,
) {
    V2BottomSheet(
        onDismissRequest = onDismiss,
    ) {
        val navController = rememberVsBottomSheetNavController(
            initialRoute = vaultList
        )

        BackHandler {
            if (navController.canGoBack) {
                navController.popBackStack()
            } else {
                onDismiss()
            }
        }

        VsBottomSheetNavHost(
            navController = navController,
            content = {
                composable<Route.VaultList> {
                    VaultListScreen(
                        openType = vaultList.openType,
                        navController = navController,
                    )
                }
                composable<Route.FolderList> {
                    FolderScreen(
                        folderId = it.folderId,
                        vaultId = it.vaultId,
                        navController = navController,
                    )
                }
                composable<Route.CreateFolder> {
                    CreateFolderScreen(
                        folderId = it.folderId,
                        navController = navController
                    )
                }
            }
        )
    }
}
