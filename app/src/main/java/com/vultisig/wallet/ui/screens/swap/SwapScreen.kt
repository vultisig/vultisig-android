package com.vultisig.wallet.ui.screens.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.presenter.common.share
import com.vultisig.wallet.presenter.keysign.KeysignFlowView
import com.vultisig.wallet.presenter.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.components.ProgressScreen
import com.vultisig.wallet.ui.models.swap.SwapViewModel
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Composable
internal fun SwapScreen(
    navController: NavController,
    vaultId: String,
    chainId: String?,
    dstTokenId: String? = null,
    viewModel: SwapViewModel = hiltViewModel(),
) {
    val swapNavHostController = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.dst.collect {
            swapNavHostController.route(it.dst.route, it.opts)
        }
    }

    val navBackStackEntry by swapNavHostController.currentBackStackEntryAsState()

    val route = navBackStackEntry?.destination?.route

    val progress = when (route) {
        SendDst.Send.route -> 0.25f
        SendDst.VerifyTransaction.staticRoute -> 0.5f
        SendDst.Keysign.staticRoute -> 0.75f
        else -> 0.0f
    }

    val topBarNavController = if (route == SendDst.Send.route) {
        navController
    } else {
        swapNavHostController
    }

    val title = when (route) {
        SendDst.Send.route -> stringResource(R.string.swap_screen_title)
        SendDst.VerifyTransaction.staticRoute -> stringResource(R.string.verify_transaction_screen_title)
        SendDst.Keysign.staticRoute -> stringResource(R.string.keysign)
        else -> stringResource(R.string.swap_screen_title)
    }

    SwapScreen(
        topBarNavController = topBarNavController,
        mainNavController = navController,
        navHostController = swapNavHostController,
        vaultId = vaultId,
        chainId = chainId,
        dstTokenId = dstTokenId,
        title = title,
        progress = progress,
        qrCodeResult = viewModel.addressProvider.address.collectAsState().value,
        onKeysignFinished = viewModel::navigateToHome,
        enableNavigationToHome = viewModel::enableNavigationToHome,
    )
}

@Composable
private fun SwapScreen(
    topBarNavController: NavController,
    mainNavController: NavController,
    navHostController: NavHostController,
    title: String,
    progress: Float,
    vaultId: String,
    chainId: String?,
    dstTokenId: String?,
    qrCodeResult: String?,
    onKeysignFinished: () -> Unit = {},
    enableNavigationToHome:() -> Unit = {},
) {
    val context = LocalContext.current

    ProgressScreen(
        navController = topBarNavController,
        title = title,
        progress = progress,
        endIcon = qrCodeResult?.takeIf { it.isNotEmpty() }?.let { R.drawable.qr_share },
        onStartIconClick =onKeysignFinished,
        onEndIconClick = qrCodeResult?.let {
            {
                val qrBitmap = generateQrBitmap(it)
                context.share(qrBitmap)
            }
        } ?: {}
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
                SwapFormScreen(
                    vaultId = vaultId,
                    chainId = chainId,
                    dstTokenId = dstTokenId,
                )
            }
            composable(
                route = SendDst.VerifyTransaction.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                VerifySwapScreen()
            }
            composable(
                route = SendDst.Keysign.staticRoute,
                arguments = SendDst.transactionArgs,
            ) { entry ->
                val transactionId = entry.arguments
                    ?.getString(SendDst.ARG_TRANSACTION_ID)!!

                val keysignShareViewModel: KeysignShareViewModel =
                    hiltViewModel(context as MainActivity)
                keysignShareViewModel.loadSwapTransaction(transactionId)

                KeysignFlowView(
                    navController = mainNavController,
                    onComplete = {
                        mainNavController.navigate(Screen.Home.route)
                    },
                    onKeysignFinished = enableNavigationToHome,
                )
            }
        }
    }
}

@Preview
@Composable
internal fun SwapScreenPreview() {
    SwapScreen(
        topBarNavController = rememberNavController(),
        mainNavController = rememberNavController(),
        navHostController = rememberNavController(),
        vaultId = "",
        chainId = null,
        dstTokenId = null,
        title = stringResource(id = R.string.swap_screen_title),
        progress = 0.35f,
        qrCodeResult = "0x1234567890"
    )
}