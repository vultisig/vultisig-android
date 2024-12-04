package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.net.nsd.NsdManager
import androidx.compose.foundation.Image
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.InformationNoteSnackBar
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.screens.keygen.GeneratingKey
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

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
        val title = if (viewModel.operationMode.value.isReshare()) {
            stringResource(id = R.string.resharing_the_vault)
        } else {
            stringResource(id = R.string.join_key_gen_screen_keygen)
        }
        when (viewModel.currentState.value) {
            JoinKeygenState.DiscoveringSessionID -> {
                DiscoveringSessionID(
                    navController = navController,
                    title = title,
                )
            }

            JoinKeygenState.DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                DiscoverService(
                    navController = navController,
                    title = title,
                )
            }

            JoinKeygenState.JoinKeygen -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.joinKeygen()
                }
                JoiningKeygen(
                    navController = navController,
                    title = title,
                )
            }

            JoinKeygenState.WaitingForKeygenStart -> {
                LaunchedEffect(key1 = viewModel) {
                    viewModel.waitForKeygenToStart()
                }
                WaitingForKeygenToStart(
                    navController = navController,
                    title = title,
                    isReshare = viewModel.operationMode.value.isReshare()
                )
            }

            JoinKeygenState.Keygen -> {
                GeneratingKey(
                    navController = navController,
                    viewModel.generatingKeyViewModel
                )
            }

            JoinKeygenState.FailedToStart -> {
                KeygenFailedToStart(
                    navController = navController,
                    errorMessage = viewModel.errorMessage.value.asString(),
                    title = if (viewModel.operationMode.value.isReshare())
                        stringResource(id = R.string.join_keygen_screen_renew)
                    else
                        stringResource(id = R.string.join_key_gen_screen_keygen),
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
internal fun DiscoveringSessionID(
    navController: NavHostController,
    title: String,
) {
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
            centerText = title,
            startIcon = R.drawable.ic_caret_left,
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
private fun PreviewDiscoveringSessionID() {
    val navController = rememberNavController()
    DiscoveringSessionID(navController = navController, title = "title")
}

@Composable
internal fun DiscoverService(
    navController: NavHostController,
    title: String,
) {
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
            centerText = title,
            startIcon = R.drawable.ic_caret_left,
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
internal fun JoiningKeygen(
    navController: NavHostController,
    title: String,
) {
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
            centerText = title,
            startIcon = R.drawable.ic_caret_left,
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
internal fun WaitingForKeygenToStart(
    navController: NavHostController,
    title: String,
    isReshare: Boolean,
) {
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
            centerText = title,
            startIcon = R.drawable.ic_caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    R.string.join_key_gen_screen_waiting_for_operationType_to_start,
                    if (isReshare) {
                        stringResource(R.string.join_keygen_screen_reshare)
                    } else {
                        stringResource(R.string.join_keygen_screen_keygen)
                    }
                ),
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
internal fun KeygenFailedToStart(
    navController: NavHostController,
    errorMessage: String,
    title: String,
) {
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
            centerText = title,
            startIcon = R.drawable.ic_caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(30.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.danger),
                contentDescription = stringResource(R.string.danger_icon),
                alignment = Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.failed_to_start_error, errorMessage),
                color = Theme.colors.neutral0,
                style = Theme.montserrat.subtitle1,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
