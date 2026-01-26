package com.vultisig.wallet.ui.screens.sign

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
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.models.send.SendViewModel
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
internal fun SignMessageScreen(
    navController: NavController,
    vaultId: VaultId,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val keysignShareViewModel: KeysignShareViewModel =
        hiltViewModel(context as MainActivity)

    val isKeysignFinished by viewModel.isKeysignFinished.collectAsState()

    val sendNav = rememberNavController()

    LaunchedEffect(Unit) {
        viewModel.dst.collect {
            sendNav.route(it.dst.route, it.opts)
        }
    }

    val navBackStackEntry by sendNav.currentBackStackEntryAsState()

    val route = navBackStackEntry?.destination?.route

    val useMainNavigator = route == SendDst.Send.route
    val progressNav = if (useMainNavigator) {
        navController
    } else {
        sendNav
    }

    val progress: Float
    val title: String

    when (route) {
        SendDst.Send.route -> {
            progress = 0.25f
            title = stringResource(R.string.sign_message_sign_screen_title)
        }
        SendDst.VerifyTransaction.staticRoute -> {
            progress = 0.5f
            title = stringResource(R.string.verify_transaction_screen_title)
        }
        else -> {
            progress = 0.0f
            title = stringResource(R.string.sign_message_sign_screen_title)
        }
    }

    val qrAddress by viewModel.addressProvider.address.collectAsState()
    val qr = qrAddress.takeIf { it.isNotEmpty() }

    VsScaffold(
        title = title,
        onBackClick = { viewModel.navigateToHome(useMainNavigator) }.takeIf { !isKeysignFinished },
        rightIcon = qr?.let { R.drawable.qr_share },
        onRightIconClick = qr?.let {
            {
                keysignShareViewModel.shareQRCode(context)
            }
        },
        content = {
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
                    SignMessageFormScreen(
                        vaultId = vaultId,
                    )
                }
                composable(
                    route = SendDst.VerifyTransaction.staticRoute,
                    arguments = SendDst.transactionArgs,
                ) {
                    VerifySignMessageScreen()
                }
            }
        }
    )
}

@Preview
@Composable
private fun SignMessageScreenPreview() {
    SignMessageScreen(
        navController = rememberNavController(),
        vaultId = "",
    )
}