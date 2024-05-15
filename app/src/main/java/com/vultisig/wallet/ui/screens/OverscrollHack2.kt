package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.presenter.home.VaultCeil
import com.vultisig.wallet.ui.components.ScrollState
import com.vultisig.wallet.ui.components.scroll

@Composable
internal fun OverscrollHack2() {
    val navController = rememberNavController()
    val vaults = VaultDB(LocalContext.current).selectAll()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .scroll(
                reverseScrolling = false,
                flingBehavior = null,
                isScrollable = true,
                isVertical = true,
                state = rememberSaveable(saver = ScrollState.Saver) {
                    ScrollState(0)
                },
            )
    ) {
        vaults.forEach { vault ->
            VaultCeil(navController, vault = vault)
        }
    }
}