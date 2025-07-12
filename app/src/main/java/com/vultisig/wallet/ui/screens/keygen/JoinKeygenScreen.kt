package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.ErrorView
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.models.keygen.JoinKeygenUiModel
import com.vultisig.wallet.ui.models.keygen.JoinKeygenViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun JoinKeygenScreen(
    model: JoinKeygenViewModel = hiltViewModel()
) {
    KeepScreenOn()

    val state by model.state.collectAsState()
    val error = state.error
    if (error == null) {
        JoinKeygenScreen(state = state)
    } else {
        ErrorView(
            errorLabel = error.message.asString(),
            buttonText = "back",
            onButtonClick = model::navigateBack
        )
    }
}

@Composable
private fun JoinKeygenScreen(
    state: JoinKeygenUiModel,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Theme.colors.backgrounds.primary,
            )
            .padding(
                all = 36.dp,
            )
    ) {
        Text(
            text = "Waiting for other devices to join",
            style = Theme.brockmann.headings.title1,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(12.dp)

        Text(
            text = "Your vault will start generating from the moment you finalize setting up your vault on your main device",
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.extraLight,
            textAlign = TextAlign.Center,
        )

        UiSpacer(36.dp)

        RiveAnimation(
            animation = R.raw.riv_connecting_with_server,
            modifier = Modifier
                .size(24.dp),
            onInit = {
                if (state.isSuccess) {
                    it.fireState("State Machine 1", "Succes")
                }
            }
        )
    }
}

@Preview
@Composable
private fun JoinKeygenScreenPreview() {
    JoinKeygenScreen(
        state = JoinKeygenUiModel(),
    )
}