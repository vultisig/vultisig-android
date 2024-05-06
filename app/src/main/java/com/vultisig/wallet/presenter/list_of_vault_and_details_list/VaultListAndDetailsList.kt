package com.vultisig.wallet.presenter.list_of_vault_and_details_list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.VaultListAndDetailsEvent.UpdateMainScreen
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.components.VaultDetailsList
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.components.VaultList

@Composable
fun VaultListAndDetailsList(navController: NavHostController) {
    val context = LocalContext.current

    val viewModel = hiltViewModel<VaultListAndDetailsViewModel>()
    val state = viewModel.state

    val isMainListVisible = state.isMainListVisible
    val selectedItem = state.selectedItem
    val loadingData = state.loadingData
    val listOfVaultNames = state.listOfVaultNames
    val listOfVaultCoins = state.listOfVaultCoins

    BackHandler {
        viewModel.onEvent(UpdateMainScreen(true))
    }


    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {

//        AnimatedTopBar(
//            navController = navController,
//            centerText = state.centerText.asString(context),
//            startIcon = R.drawable.hamburger_menu,
//            endIcon = R.drawable.clockwise,
//            isBottomLayerVisible = isMainListVisible,
//            hasCenterArrow = true,
//            onCenterTextClick = {
//                viewModel.onEvent(UpdateMainScreen(true))
//            },
//            onBackClick = {
//                viewModel.onEvent(UpdateMainScreen(true))
//            }
//        )
        Spacer(modifier =Modifier.height( MaterialTheme.dimens.medium1))
        Box (
            contentAlignment = Alignment.Center

        ){

            VaultList(navController = navController,listOfVaultNames)

            this@Column.AnimatedVisibility(
                visible = isMainListVisible.not(),
                enter = slideInVertically() + fadeIn()
            ) {
                VaultDetailsList(navController = navController, listOfVaultCoins)
            }

            this@Column.AnimatedVisibility(
                visible = loadingData,
                enter = fadeIn()
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.appColor.turquoise800,
                    modifier = Modifier.size(MaterialTheme.dimens.circularMedium1)
                )
            }
        }
    }


}

@Preview(showBackground = true)
@Composable
fun VaultListAndDetailsListPreview() {
    val navController = rememberNavController()
    VaultListAndDetailsList( navController)
}