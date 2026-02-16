package com.vultisig.wallet.ui.screens.keysign

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.peer.ConnectingToServer
import com.vultisig.wallet.ui.screens.peer.PeerDiscoveryScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.forCanvasMinify
import timber.log.Timber

@Composable
internal fun KeysignPeerDiscovery(
    viewModel: KeysignFlowViewModel,
    txType: Route.Keysign.Keysign.TxType,
    sharedViewModel: KeysignShareViewModel =
        hiltViewModel(LocalActivity.current as MainActivity)
) {
    KeepScreenOn()

    val activity = LocalContext.current
    val uiModel by viewModel.uiState.collectAsState()

    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.collectAsState(initial = emptyList()).value
    val context = LocalContext.current.applicationContext


    LaunchedEffect(txType) {
        viewModel.setData(
            shareViewModel = sharedViewModel,
            context = context,
            txType = txType
        )
    }

    val isSwap = uiModel.isSwap
    val qrShareTitle = if (isSwap)
        stringResource(R.string.qr_title_join_swap_keysign)
    else
        stringResource(R.string.qr_title_join_keysign)

    val qrShareBackground = Theme.v2.colors.backgrounds.primary

    val vault = uiModel.vault
    val qrShareDescription =
        if (isSwap)
            stringResource(
                R.string.qr_title_join_keysign_swap_description,
                vault.name.forCanvasMinify(),
                uiModel.amount.forCanvasMinify(),
                uiModel.toAmount.forCanvasMinify(),
            ) else
            stringResource(
                R.string.qr_title_join_keysign_description,
                vault.name.forCanvasMinify(),
                uiModel.amount.forCanvasMinify(),
                uiModel.toAddress.forCanvasMinify(),
            )


    LaunchedEffect(key1 = viewModel.participants) {
        viewModel.participants.collect { newList ->
            // add all participants to the selection
            for (participant in newList) {
                viewModel.addParticipant(participant)
            }
        }
    }
    LaunchedEffect(key1 = viewModel.selection) {
        viewModel.selection.asFlow().collect { newList ->
            if (vault.signers.isEmpty()) {
                Timber.e("Vault signers size is 0")
                return@collect
            }
            if (newList.size >= Utils.getThreshold(vault.signers.size)) {
                // automatically kickoff keysign
                viewModel.moveToState(KeysignFlowState.Keysign)
            }
        }
    }

    LaunchedEffect(viewModel.keysignMessage.value) {
        if (viewModel.keysignMessage.value.isNotEmpty()) {
            sharedViewModel.loadQrPainter(viewModel.keysignMessage.value)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopParticipantDiscovery()
        }
    }

    LaunchedEffect(uiModel.qrBitmapPainter) {
        sharedViewModel.saveShareQrBitmap(
            context,
            qrShareBackground.toArgb(),
            qrShareTitle,
            qrShareDescription,
            BitmapFactory.decodeResource(
                context.resources, R.drawable.ic_share_qr_logo
            )
        )
    }

    KeysignPeerDiscovery(
        localPartyId = vault.localPartyID,
        isLookingForVultiServer = viewModel.isFastSign &&
                Utils.getThreshold(vault.signers.size) == 2,
        minimumDevices = Utils.getThreshold(vault.signers.size),
        selectionState = selectionState,
        participants = participants,
        bitmapPainter = uiModel.qrBitmapPainter,
        networkPromptOption = viewModel.networkOption.value,
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, context) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onShareQrClick = { sharedViewModel.shareQRCode(activity) },
        onStopParticipantDiscovery = viewModel::moveToKeysignState,
        onBackClick = viewModel::back,
    )
}

@Composable
private fun KeysignPeerDiscovery(
    isLookingForVultiServer: Boolean,
    localPartyId: String,
    minimumDevices: Int,
    selectionState: List<String>,
    participants: List<String>,
    bitmapPainter: BitmapPainter?,
    networkPromptOption: NetworkOption,
    onChangeNetwork: (NetworkOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
    onShareQrClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    if (isLookingForVultiServer) {
        ConnectingToServer(false)
    } else {
        PeerDiscoveryScreen(
            state = PeerDiscoveryUiModel(
                qr = bitmapPainter,
                network = networkPromptOption,
                localPartyId = localPartyId,
                devices = participants.filter { it != localPartyId },
                selectedDevices = selectionState.filter { it != localPartyId },
                minimumDevices = minimumDevices,
                minimumDevicesDisplayed = minimumDevices,
                showQrHelpModal = false,
                showDevicesHint = false,
                connectingToServer = null,
                error = null,
            ),

            onBackClick = onBackClick,

            showHelp = false,
            onHelpClick = {},
            onShareQrClick = onShareQrClick,

            onCloseHintClick = {},
            onDismissQrHelpModal = {},

            onSwitchModeClick = {
                onChangeNetwork(
                    when (networkPromptOption) {
                        NetworkOption.Internet -> NetworkOption.Local
                        NetworkOption.Local -> NetworkOption.Internet
                    }
                )
            },
            onDeviceClick = { device ->
                if (device in selectionState) {
                    onRemoveParticipant(device)
                } else {
                    onAddParticipant(device)
                }
            },
            onNextClick = onStopParticipantDiscovery,
        )
    }
}

@Preview
@Composable
private fun KeysignPeerDiscoveryPreview() {
    KeysignPeerDiscovery(
        isLookingForVultiServer = true,
        selectionState = listOf("1", "2"),
        localPartyId = "1",
        minimumDevices = 2,
        participants = listOf("1", "2", "3"),
        bitmapPainter = BitmapPainter(
            createBitmap(1, 1).asImageBitmap(),
            filterQuality = FilterQuality.None
        ),
        networkPromptOption = NetworkOption.Local,
    )
}