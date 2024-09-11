package com.vultisig.wallet.ui.screens.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.ui.screens.keysign.KeysignFlowView
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.components.ProgressScreen
import com.vultisig.wallet.ui.models.send.SendViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordScreen
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
internal fun SendScreen(
    navController: NavController,
    vaultId: String,
    chainId: String?,
    startWithTokenId: String?,
    qrCodeResult: String?,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val sendNav = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.dst.collect {
            sendNav.route(it.dst.route, it.opts)
        }
    }

    val navBackStackEntry by sendNav.currentBackStackEntryAsState()

    val route = navBackStackEntry?.destination?.route

    val progress = when (route) {
        SendDst.Send.route -> 0.35f
        SendDst.VerifyTransaction.staticRoute -> 0.5f
        SendDst.Password.staticRoute -> 0.65f
        SendDst.Keysign.staticRoute -> 0.75f
        else -> 0.0f
    }

    val progressNav = if (route == SendDst.Send.route) {
        navController
    } else {
        sendNav
    }

    val title = when (route) {
        SendDst.Send.route -> stringResource(R.string.send_screen_title)
        SendDst.VerifyTransaction.staticRoute -> stringResource(R.string.verify_transaction_screen_title)
        SendDst.Password.staticRoute -> stringResource(R.string.keysign_password_title)
        SendDst.Keysign.staticRoute -> stringResource(R.string.keysign)
        else -> stringResource(R.string.send_screen_title)
    }

    val qrAddress by viewModel.addressProvider.address.collectAsState()
    val qr = qrAddress.takeIf { it.isNotEmpty() }
    ProgressScreen(
        navController = progressNav,
        title = title,
        progress = progress,
        endIcon = qr?.let { R.drawable.qr_share },
        onEndIconClick = qr?.let {
            {
                viewModel.shareQRCode(context)
            }
        } ?: {},
        onStartIconClick =viewModel::navigateToHome,
    ) {
        NavHost(
            navController = sendNav,
            startDestination = SendDst.Send.route,
            enterTransition = slideInFromEndEnterTransition(),
            exitTransition = slideOutToStartExitTransition(),
            popEnterTransition = slideInFromStartEnterTransition(),
            popExitTransition = slideOutToEndExitTransition(),
        ) {
            composable(
                route = SendDst.Send.route,
            ) {
                SendFormScreen(
                    vaultId = vaultId,
                    chainId = chainId,
                    startWithTokenId = startWithTokenId,
                    qrCodeResult = qrCodeResult,
                )
            }
            composable(
                route = SendDst.VerifyTransaction.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                VerifyTransactionScreen()
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

                val keysignShareViewModel: KeysignShareViewModel =
                    hiltViewModel(context as MainActivity)
                keysignShareViewModel.loadTransaction(transactionId)

                KeysignFlowView(
                    navController = navController,
                    onComplete = {
                        navController.navigate(Destination.Home().route)
                    },
                    onKeysignFinished = {
                        viewModel.enableNavigationToHome()
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendScreen(
        navController = rememberNavController(),
        qrCodeResult = null,
        vaultId = "",
        chainId = "",
        startWithTokenId = null,
    )
}