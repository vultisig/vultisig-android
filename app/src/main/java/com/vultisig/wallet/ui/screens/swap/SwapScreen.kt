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
import com.vultisig.wallet.ui.components.ProgressScreen
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.models.swap.SwapViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.screens.keysign.KeysignFlowView
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordScreen
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Composable
internal fun SwapScreen(
    navController: NavController,
    vaultId: String,
    chainId: String?,
    srcTokenId: String? = null,
    dstTokenId: String? = null,
    viewModel: SwapViewModel = hiltViewModel(),
) {
    val swapNavHostController = rememberNavController()

    val isKeysignFinished by viewModel.isKeysignFinished.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dst.collect {
            swapNavHostController.route(it.dst.route, it.opts)
        }
    }

    val navBackStackEntry by swapNavHostController.currentBackStackEntryAsState()

    val route = navBackStackEntry?.destination?.route

    val useMainNavigator = route == SendDst.Send.route

    val topBarNavController = if (useMainNavigator) {
        navController
    } else {
        swapNavHostController
    }

    val progress: Float
    val title: String

    when {
        route == SendDst.Send.route -> {
            progress = 0.25f
            title = stringResource(R.string.swap_screen_title)
        }
        route == SendDst.VerifyTransaction.staticRoute -> {
            progress = 0.5f
            title = stringResource(R.string.verify_transaction_screen_title)
        }
        route == SendDst.Password.staticRoute -> {
            progress = 0.65f
            title = stringResource(id = R.string.keygen_password_title)
        }
        route == SendDst.Keysign.staticRoute && isKeysignFinished -> {
            progress = 1f
            title = stringResource(R.string.transaction_complete_screen_title)
        }
        route == SendDst.Keysign.staticRoute -> {
            progress = 0.75f
            title = stringResource(R.string.keysign)
        }
        else -> {
            progress = 0.0f
            title = stringResource(R.string.swap_screen_title)
        }
    }

    SwapScreen(
        topBarNavController = topBarNavController,
        navHostController = swapNavHostController,
        vaultId = vaultId,
        chainId = chainId,
        srcTokenId = srcTokenId,
        dstTokenId = dstTokenId,
        title = title,
        progress = progress,
        qrCodeResult = viewModel.addressProvider.address.collectAsState().value,
        navigateToHome = { viewModel.navigateToHome(useMainNavigator) },
        finishKeysign = viewModel::finishKeysign,
        onRefreshQuoteClick = viewModel::requestToRefreshQuote
    )
}

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
private fun SwapScreen(
    topBarNavController: NavController,
    navHostController: NavHostController,
    title: String,
    progress: Float,
    vaultId: String,
    chainId: String?,
    srcTokenId: String?,
    dstTokenId: String?,
    qrCodeResult: String?,
    navigateToHome: () -> Unit = {},
    finishKeysign: (() -> Unit)? = null,
    onRefreshQuoteClick: () -> Unit = {},
) {
    val context = LocalContext.current

    val keysignShareViewModel: KeysignShareViewModel =
        hiltViewModel(context as MainActivity)

    val showRefreshQuoteIcon = progress == 0.25f

    ProgressScreen(
        navController = topBarNavController,
        title = title,
        progress = progress,
        endIcon =
        if (showRefreshQuoteIcon)
            R.drawable.arrow_clockwise
        else
            qrCodeResult?.takeIf { it.isNotEmpty() }?.let { R.drawable.qr_share },
        onStartIconClick = navigateToHome,
        onEndIconClick = if (showRefreshQuoteIcon) {
            onRefreshQuoteClick
        } else qrCodeResult?.let {
            {
                keysignShareViewModel.shareQRCode(context)
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
                    srcTokenId = srcTokenId,
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
                route = SendDst.Password.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                KeysignPasswordScreen()
            }
            composable(
                route = SendDst.Keysign.staticRoute,
                arguments = SendDst.transactionArgs,
            ) { entry ->
                val transactionId = entry.arguments
                    ?.getString(SendDst.ARG_TRANSACTION_ID)!!
                keysignShareViewModel.loadSwapTransaction(transactionId)

                KeysignFlowView(
                    onComplete = navigateToHome,
                    onKeysignFinished = finishKeysign,
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
        navHostController = rememberNavController(),
        vaultId = "",
        chainId = null,
        srcTokenId = null,
        dstTokenId = null,
        title = stringResource(id = R.string.swap_screen_title),
        progress = 0.35f,
        qrCodeResult = "0x1234567890",
    )
}