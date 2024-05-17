package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keysign.KeysignState
import com.vultisig.wallet.presenter.keysign.KeysignViewModel
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun Keysign(
    navController: NavController,
    viewModel: KeysignViewModel
) {
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.startKeysign()
    }

    UiBarContainer(
        navController = navController,
        title = stringResource(id = R.string.keysign)
    ) {
        KeysignScreen(
            state = viewModel.currentState.collectAsState().value,
            errorMessage = viewModel.errorMessage.value,
        )
    }
}

@Composable
internal fun KeysignScreen(
    state: KeysignState,
    errorMessage: String,
) {
    KeepScreenOn()

    val textColor = Theme.colors.neutral0

    val text = when (state) {
        KeysignState.CreatingInstance -> "Preparing vault..."
        KeysignState.KeysignECDSA -> "Signing with ECDSA..."
        KeysignState.KeysignEdDSA -> "Signing with EdDSA..."
        KeysignState.KeysignFinished -> "Keysign Finished!"
        KeysignState.ERROR -> "Error! Please try again. $errorMessage"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(weight = 1f)
        Text(
            text = text,
            color = Theme.colors.neutral0,
            style = Theme.menlo.subtitle1
        )

        UiSpacer(size = 32.dp)

        UiCirclesLoader()

        UiSpacer(weight = 1f)

        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = Theme.colors.turquoise600Main
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with vultisig open.",
            color = textColor,
            style = Theme.menlo.body1,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 80.dp)
    }
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignScreen(
        state = KeysignState.CreatingInstance,
        errorMessage = "Error,"
    )
}
