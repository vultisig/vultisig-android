package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.presenter.home.VaultCeil
import timber.log.Timber

@Composable
internal fun OverscrollHack1() {
    val navController = rememberNavController()
    val vaults = VaultDB(LocalContext.current).selectAll()

    val localDensity = LocalDensity.current

    var columnHeightDp by remember {
        mutableStateOf(0.dp)
    }

    var containerHeightDp by remember {
        mutableStateOf(0.dp)
    }

    Timber.e("recomposition")

    Column(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                // Set column height using the LayoutCoordinates
                containerHeightDp = with(localDensity) { coordinates.size.height.toDp() }
            }
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    // Set column height using the LayoutCoordinates
                    columnHeightDp = with(localDensity) { coordinates.size.height.toDp() }
                }
        ) {
            vaults.forEach { vault ->
                VaultCeil(navController, vault = vault)
            }
        }

        Spacer(modifier = Modifier
            .height(containerHeightDp - columnHeightDp + 1.dp)
            .fillMaxWidth()
        )
    }
}