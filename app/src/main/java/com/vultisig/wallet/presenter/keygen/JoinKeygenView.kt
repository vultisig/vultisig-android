package com.vultisig.wallet.presenter.keygen

import android.content.Context
import android.net.nsd.NsdManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.ui.components.InformationNoteSnackBar
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.screens.keygen.GeneratingKey
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun JoinKeygenView(
    navController: NavHostController,
    qrCodeResult: String,
    viewModel: JoinKeygenViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val context = LocalContext.current

    LaunchedEffect(qrCodeResult) {
        viewModel.setScanResult(qrCodeResult)
    }

    DisposableEffect(key1 = Unit) {
        onDispose {
            viewModel.cleanUp()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (viewModel.currentState.value) {
            JoinKeygenState.DiscoveryingSessionID -> {
                DiscoveryingSessionID(navController = navController)
            }

            JoinKeygenState.DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                DiscoverService(navController = navController)
            }

            JoinKeygenState.JoinKeygen -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.joinKeygen()
                }
                JoiningKeygen(navController = navController)
            }

            JoinKeygenState.WaitingForKeygenStart -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.waitForKeygenToStart()
                }
                WaitingForKeygenToStart(navController = navController)
            }

            JoinKeygenState.Keygen -> {
                GeneratingKey(navController = navController, viewModel.generatingKeyViewModel)
            }

            JoinKeygenState.FailedToStart -> {
                KeygenFailedToStart(
                    navController = navController,
                    errorMessage = viewModel.errorMessage.value
                )
            }

            JoinKeygenState.ERROR -> {
                KeygenFailedToStart(
                    navController = navController,
                    errorMessage = viewModel.errorMessage.value
                )
            }
        }
        SnackbarHost(
            modifier = Modifier.align(Alignment.BottomCenter),
            hostState = viewModel.warningHostState
        ){
            InformationNoteSnackBar(text = stringResource(id = R.string.keygen_info_note))
        }
    }
}

@Composable
fun DiscoveryingSessionID(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.join_key_gen_screen_keygen), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.join_key_gen_screen_discovering_session_id),
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDiscoveryingSessionID() {
    val navController = rememberNavController()
    DiscoveryingSessionID(navController = navController)
}

@Composable
fun DiscoverService(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(R.string.join_key_gen_screen_keygen), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.join_key_gen_screen_discovering_service),
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun JoiningKeygen(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.join_key_gen_screen_keygen), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.join_key_gen_screen_joining_keygen),
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun WaitingForKeygenToStart(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.join_key_gen_screen_keygen), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.join_key_gen_screen_waiting_for_keygen_to_start),
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2
            )
            CircularProgressIndicator(
                color = Theme.colors.neutral0,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun KeygenFailedToStart(navController: NavHostController, errorMessage: String) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.join_key_gen_screen_keygen), startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.failed_to_start_error, errorMessage),
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2
            )
        }
    }
}
