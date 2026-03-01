package com.vultisig.wallet.ui.screens.v3.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.onboarding.SetupVaultInfoEvent
import com.vultisig.wallet.ui.models.v3.onboarding.SetupVaultInfoUiState
import com.vultisig.wallet.ui.models.v3.onboarding.SetupVaultInfoViewModel
import com.vultisig.wallet.ui.screens.v3.onboarding.components.SetupVaultGuideItem
import com.vultisig.wallet.ui.screens.v3.onboarding.components.SetupVaultHeader
import com.vultisig.wallet.ui.screens.v3.onboarding.components.SetupVaultRive
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString


@Composable
internal fun SetupVaultInfoScreen(
    viewModel: SetupVaultInfoViewModel = hiltViewModel()
){
    val uiState by viewModel.uiState.collectAsState()

    SetupVaultInfoScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )

}

@Composable
private fun SetupVaultInfoScreen(
    uiState: SetupVaultInfoUiState,
    onEvent: (SetupVaultInfoEvent) -> Unit
){
    V3Scaffold(
        onBackClick = {
            onEvent(SetupVaultInfoEvent.Back)
        },
        applyDefaultPaddings = false,
    ){
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = V3Scaffold.PADDING_HORIZONTAL,
                    )
            ) {
                Text(
                    text = stringResource(R.string.vault_setup_your_vault_setup),
                    color = Theme.v2.colors.neutrals.n50,
                    style = Theme.brockmann.headings.title2
                )
                UiSpacer(
                    size = 20.dp
                )

                SetupVaultHeader(
                    logo = uiState.headerLogo,
                    title =  uiState.title.asString(),
                    subTitle = uiState.subTitle.asString(),
                )

            }

            UiSpacer(
                size = 14.dp
            )

            SetupVaultRive(
                animationRes = uiState.rive
            )

            UiSpacer(
                size = 33.dp
            )

            Column(
                modifier = Modifier
                    .padding(
                        horizontal = V3Scaffold.PADDING_HORIZONTAL,
                        vertical = V3Scaffold.PADDING_VERTICAL,
                    )
            ) {

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.tips.forEach {(logo, title, subTitle) ->
                        SetupVaultGuideItem(
                            logo = logo,
                            title = title.asString(),
                            subTitle = subTitle.asString()
                        )
                    }
                }


                UiSpacer(
                    weight = 1f
                )

                VsButton(
                    label = stringResource(R.string.key_import_device_count_get_started),
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        onEvent(SetupVaultInfoEvent.Next)
                    }
                )
            }
        }
    }
}

@Composable
@Preview
private fun SetupVaultInfoScreenPreview(){
    SetupVaultInfoScreen(
        uiState = SetupVaultInfoUiState(

        ),
        onEvent = {}
    )
}