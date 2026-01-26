package com.vultisig.wallet.ui.screens.deposit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.deposit.DepositViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Composable
internal fun DepositScreen(
    navController: NavController,
    vaultId: String,
    chainId: String,
    viewModel: DepositViewModel = hiltViewModel(),
    depositType: String? = null,
    bondAddress: String? = null,
) {
    val depositNavHostController = rememberNavController()
    val context = LocalContext.current
    val keysignShareViewModel: KeysignShareViewModel =
        hiltViewModel(context as MainActivity)

    val isKeysignFinished by viewModel.isKeysignFinished.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dst.collect {
            depositNavHostController.route(it.dst.route, it.opts)
        }
    }

    val navBackStackEntry by depositNavHostController.currentBackStackEntryAsState()

    val route = navBackStackEntry?.destination?.route

    val shouldUseMainNavigator = route == SendDst.Send.route
    val topBarNavController = if (shouldUseMainNavigator) {
        navController
    } else {
        depositNavHostController
    }

    val progress: Float
    val title: String

    when (route) {
        SendDst.Send.route -> {
            progress = 0.25f
            title = stringResource(R.string.deposit_screen_title)
        }
        SendDst.VerifyTransaction.staticRoute -> {
            progress = 0.5f
            title = stringResource(R.string.verify_transaction_screen_title)
        }
        else -> {
            progress = 0.0f
            title = stringResource(R.string.deposit_screen_title)
        }
    }
    val qrAddress by viewModel.addressProvider.address.collectAsState()
    val qr = qrAddress.takeIf { it.isNotEmpty() }
    DepositScreen(
        navHostController = depositNavHostController,
        vaultId = vaultId,
        chainId = chainId,
        title = title,
        showStartIcon = !isKeysignFinished,
        endIcon = qr?.let { R.drawable.qr_share },
        endIconClick = qr?.let {
            { keysignShareViewModel.shareQRCode(context) }
        } ?: {},
        onKeysignFinished = { viewModel.navigateToHome(shouldUseMainNavigator) },
        depositType = depositType,
        bondAddress = bondAddress,
    )
}

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
private fun DepositScreen(
    navHostController: NavHostController,
    title: String,
    vaultId: String,
    chainId: String,
    showStartIcon: Boolean,
    endIcon: Int? = null,
    endIconClick: () -> Unit = {},
    onKeysignFinished: () -> Unit = {},
    depositType: String? = null,
    bondAddress: String? = null,
) {


    VsScaffold(
        title = title,
        onBackClick = onKeysignFinished.takeIf { showStartIcon },
        rightIcon = endIcon,
        onRightIconClick = endIconClick,
    ) {
        NavHost(
            navController = navHostController,
            startDestination = SendDst.Send.route,
            enterTransition = slideInFromEndEnterTransition(),
            exitTransition = slideOutToStartExitTransition(),
            popEnterTransition = slideInFromStartEnterTransition(),
            popExitTransition = slideOutToEndExitTransition(),
        ) {
            composable(
                route = SendDst.Send.route,
            ) {
                DepositFormScreen(
                    vaultId = vaultId,
                    chainId = chainId,
                    depositType = depositType,
                    bondAddress = bondAddress,
                )
            }
            composable(
                route = SendDst.VerifyTransaction.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                VerifyDepositScreen()
            }
        }
    }
}