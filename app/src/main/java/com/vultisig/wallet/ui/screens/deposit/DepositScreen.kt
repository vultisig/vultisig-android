package com.vultisig.wallet.ui.screens.deposit

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
import com.vultisig.wallet.ui.screens.keysign.KeysignFlowView
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.components.ProgressScreen
import com.vultisig.wallet.ui.models.deposit.DepositViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordScreen
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
) {
    val depositNavHostController = rememberNavController()

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

    when {
        route == SendDst.Send.route -> {
            progress = 0.25f
            title = stringResource(R.string.deposit_screen_title)
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
            title = stringResource(R.string.deposit_screen_title)
        }
    }

    DepositScreen(
        topBarNavController = topBarNavController,
        navHostController = depositNavHostController,
        vaultId = vaultId,
        chainId = chainId,
        title = title,
        progress = progress,
        showStartIcon = !isKeysignFinished,
        onKeysignFinished = { viewModel.navigateToHome(shouldUseMainNavigator) },
        finishKeysign = viewModel::finishKeysign,
    )
}

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
private fun DepositScreen(
    topBarNavController: NavController,
    navHostController: NavHostController,
    title: String,
    progress: Float,
    vaultId: String,
    chainId: String,
    showStartIcon: Boolean,
    onKeysignFinished: () -> Unit = {},
    finishKeysign:() -> Unit = {},
) {
    val context = LocalContext.current
    ProgressScreen(
        navController = topBarNavController,
        title = title,
        progress = progress,
        showStartIcon = showStartIcon,
        onStartIconClick = onKeysignFinished,
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
                )
            }
            composable(
                route = SendDst.VerifyTransaction.staticRoute,
                arguments = SendDst.transactionArgs,
            ) {
                VerifyDepositScreen()
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
                keysignShareViewModel.loadDepositTransaction(transactionId)

                KeysignFlowView(
                    onComplete = {
                        onKeysignFinished()
                    },
                    onKeysignFinished = finishKeysign,
                )
            }
        }
    }
}

@Preview
@Composable
internal fun DepositScreenPreview() {
    DepositScreen(
        topBarNavController = rememberNavController(),
        navHostController = rememberNavController(),
        vaultId = "",
        chainId = "",
        showStartIcon = true,
        title = stringResource(id = R.string.deposit_screen_title),
        progress = 0.35f,
    )
}