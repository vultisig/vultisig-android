package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.models.keygen.KeygenFlowViewModel
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.models.keygen.VaultSetupType.Companion.asString
import com.vultisig.wallet.ui.screens.PeerDiscoveryView
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.NetworkPromptOption

@Composable
internal fun KeygenPeerDiscovery(
    navController: NavHostController,
    viewModel: KeygenFlowViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    KeygenPeerDiscoveryScreen(
        navController = navController,
        isLookingForVultiServer = uiState.vaultSetupType == VaultSetupType.FAST,
        selectionState = uiState.selection,
        isReshare = uiState.isReshareMode,
        participants = uiState.participants,
        keygenPayloadState = uiState.keygenPayload,
        vaultSetupType = uiState.vaultSetupType.asString(),
        networkPromptOption = uiState.networkOption,
        isContinueEnabled = uiState.isContinueButtonEnabled,
        onQrAddressClick = { viewModel.shareQRCode(context) },
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, applicationContext) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = {
            viewModel.finishPeerDiscovery()
        },
    )
}

@Composable
internal fun KeygenPeerDiscoveryScreen(
    navController: NavHostController,
    isLookingForVultiServer: Boolean,
    selectionState: List<String>,
    participants: List<String>,
    keygenPayloadState: String,
    vaultSetupType: String,
    networkPromptOption: NetworkPromptOption,
    isContinueEnabled: Boolean,
    onQrAddressClick: () -> Unit = {},
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
    isReshare: Boolean,
) {

    UiBarContainer(
        navController = navController,
        title = if (isReshare)
            stringResource(id = R.string.resharing_the_vault)
        else
            stringResource(
                R.string.keygen_peer_discovery_keygen,
                vaultSetupType
            ),
        endIcon = R.drawable.qr_share,
        onEndIconClick = onQrAddressClick
    ) {
        if (isLookingForVultiServer) {
            FastPeerDiscovery()
        } else {
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {

                PeerDiscoveryView(
                    modifier = Modifier.weight(1f),
                    selectionState = selectionState,
                    participants = participants,
                    keygenPayloadState = keygenPayloadState,
                    networkPromptOption = networkPromptOption,
                    onChangeNetwork = onChangeNetwork,
                    onAddParticipant = onAddParticipant,
                    onRemoveParticipant = onRemoveParticipant,
                )

                MultiColorButton(
                    text = stringResource(R.string.keygen_peer_discovery_continue),
                    backgroundColor = Theme.colors.turquoise600Main,
                    textColor = Theme.colors.oxfordBlue600Main,
                    minHeight = 44.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    disabled = !isContinueEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 16.dp,
                        ),
                    onClick = onStopParticipantDiscovery,
                )
            }
        }
    }
}

@Composable
internal fun FastPeerDiscovery() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(size = 74.dp)

        Text(
            text = stringResource(R.string.keygen_vultiserver_peer_discovery_waiting),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 48.dp)
        )

        UiSpacer(size = 48.dp)

        UiCirclesLoader()
    }
}

@Preview
@Composable
private fun KeygenPeerDiscoveryScreenPreview() {
    KeygenPeerDiscoveryScreen(
        navController = rememberNavController(),
        isLookingForVultiServer = true,
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keygenPayloadState = "keygenPayloadState",
        networkPromptOption = NetworkPromptOption.LOCAL,
        isContinueEnabled = true,
        vaultSetupType = "M/N",
        isReshare = true,
    )
}
