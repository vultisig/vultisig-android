package com.vultisig.wallet.ui.screens.keygen

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keygen.GeneratingKeyViewModel
import com.vultisig.wallet.presenter.keygen.KeygenState
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.components.library.UiCircularProgressIndicator
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.min

@Composable
internal fun GeneratingKey(
    navController: NavHostController,
    viewModel: GeneratingKeyViewModel,
) {
    KeepScreenOn()
    val context: Context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.generateKey()
    }

    DisposableEffect(key1 = Unit) {
        onDispose {
            viewModel.stopService(context)
        }
    }

    val state = viewModel.currentState.value

    when (state) {
        KeygenState.ERROR -> {
            LaunchedEffect(key1 = viewModel) {
                // stop the service , restart it when user need it
                viewModel.stopService(context)
            }
        }

        KeygenState.Success -> {
            LaunchedEffect(Unit) {
                viewModel.saveVault(context)
            }
        }

        else -> Unit
    }

    GeneratingKey(
        navController = navController,
        keygenState = viewModel.currentState.value,
        errorMessage = viewModel.errorMessage.value,
    )
}

@Composable
internal fun GeneratingKey(
    navController: NavHostController,
    keygenState: KeygenState,
    errorMessage: String
) {
    val textColor = Theme.colors.neutral0
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 16.dp,
                horizontal = 12.dp
            ),
        topBar = {
            TopBar(
                centerText = stringResource(R.string.generating_key_title),
                startIcon = R.drawable.caret_left,
                navController = navController
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = CenterHorizontally
            ) {
                DevicesOnSameNetworkHint(
                    title = stringResource(R.string.generating_key_screen_keep_devices_on_the_same_wifi_network_with_vultisig_open),
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = Alignment.Center
        ) {
            when (keygenState) {
                KeygenState.CreatingInstance,
                KeygenState.KeygenECDSA,
                KeygenState.KeygenEdDSA,
                KeygenState.ReshareECDSA,
                KeygenState.ReshareEdDSA,
                KeygenState.Success -> {
                    val title = when (keygenState) {
                        KeygenState.CreatingInstance -> stringResource(R.string.generating_key_preparing_vault)
                        KeygenState.KeygenECDSA -> stringResource(R.string.generating_key_screen_generating_ecdsa_key)
                        KeygenState.KeygenEdDSA -> stringResource(R.string.generating_key_screen_generating_eddsa_key)
                        KeygenState.ReshareECDSA -> stringResource(R.string.generating_key_screen_resharing_ecdsa_key)
                        KeygenState.ReshareEdDSA -> stringResource(R.string.generating_key_screen_resharing_eddsa_key)
                        KeygenState.Success -> stringResource(R.string.generating_key_screen_success)
                        else -> ""
                    }

                    val progress = when (keygenState) {
                        KeygenState.CreatingInstance -> 0.25f
                        KeygenState.KeygenECDSA -> 0.5f
                        KeygenState.KeygenEdDSA -> 0.75f
                        KeygenState.ReshareECDSA -> 0.5f
                        KeygenState.ReshareEdDSA -> 0.75f
                        KeygenState.Success -> 1.0f
                        else -> 0F
                    }

                    KeygenIndicator(
                        statusText = title,
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                KeygenState.ERROR -> {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.generating_key_screen_keygen_failed),
                            color = textColor,
                            style = Theme.menlo.heading5
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = textColor,
                            style = Theme.menlo.body2
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun KeygenIndicator(
    statusText: String,
    progress: Float,
    modifier: Modifier
) {
    val progressAnimated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "KeygenIndicatorProgress"
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(32.dp),
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            Text(
                text = statusText,
                color = Theme.colors.neutral0,
                style = Theme.menlo.body2,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 16.dp)

            UiCirclesLoader()
        }

        val minDimen = rememberSaveable {
            min(maxWidth.value, maxHeight.value)
        }

        val isLandscape = maxWidth > maxHeight

        UiCircularProgressIndicator(
            progress = { progressAnimated },
            strokeWidth = 16.dp,
            modifier = Modifier
                .width(minDimen.dp.minus(if (isLandscape) 64.dp else 0.dp))
                .aspectRatio(1f)
        )
    }
}

@Preview
@Composable
private fun GeneratingKeyPreview() {
    GeneratingKey(
        navController = rememberNavController(),
        keygenState = KeygenState.CreatingInstance,
        errorMessage = ""
    )
}
