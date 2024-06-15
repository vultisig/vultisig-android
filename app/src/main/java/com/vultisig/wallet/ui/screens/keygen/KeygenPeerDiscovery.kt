package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.presenter.common.share
import com.vultisig.wallet.presenter.keygen.KeygenFlowState
import com.vultisig.wallet.presenter.keygen.KeygenFlowViewModel
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.models.keygen.VaultSetupType.Companion.asString
import com.vultisig.wallet.ui.screens.PeerDiscoveryView
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeygenPeerDiscovery(
    navController: NavHostController,
    vaultId: String,
    viewModel: KeygenFlowViewModel,
) {

    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.asFlow().collectAsState(initial = emptyList()).value

    val keygenPayloadState = viewModel.keygenPayloadState.value

    val networkPromptOption = viewModel.networkOption.value

    val context = LocalContext.current
    val applicationContext = context.applicationContext

    LaunchedEffect(Unit) {
        viewModel.setData(vaultId, applicationContext)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopParticipantDiscovery()
        }
    }

    KeygenPeerDiscoveryScreen(
        navController = navController,
        selectionState = selectionState,
        participants = participants,
        keygenPayloadState = keygenPayloadState,
        vaultSetupType = viewModel.vaultSetupType.asString(),
        networkPromptOption = networkPromptOption,
        onQrAddressClick = {
            val qrBitmap = generateQrBitmap(keygenPayloadState)
            context.share(qrBitmap)
        },
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, applicationContext) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = {
            viewModel.stopParticipantDiscovery()
            viewModel.moveToState(KeygenFlowState.DEVICE_CONFIRMATION)
        },
    )
}

@Composable
internal fun KeygenPeerDiscoveryScreen(
    navController: NavHostController,
    selectionState: List<String>,
    participants: List<String>,
    keygenPayloadState: String,
    vaultSetupType: String,
    networkPromptOption: NetworkPromptOption,
    onQrAddressClick: () -> Unit = {},
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
) {
    val textColor = Theme.colors.neutral0

    UiBarContainer(
        navController = navController,
        title = stringResource(
            R.string.keygen_peer_discovery_keygen,
            vaultSetupType
        ),
        endIcon = R.drawable.qr_share,
        onEndIconClick = onQrAddressClick
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if (selectionState.isNotEmpty() && selectionState.count() > 1) {
                Text(
                    text = stringResource(
                        R.string.keygen_peer_descovery_of_vault,
                        Utils.getThreshold(selectionState.count()),
                        selectionState.count()
                    ),
                    color = textColor,
                    style = Theme.montserrat.subtitle2
                )
            }

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
                disabled = selectionState.size < 2,
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

@Preview
@Composable
private fun KeygenPeerDiscoveryScreenPreview() {
    KeygenPeerDiscoveryScreen(
        navController = rememberNavController(),
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keygenPayloadState = "keygenPayloadState",
        networkPromptOption = NetworkPromptOption.LOCAL,
        vaultSetupType = "M/N",
    )
}
