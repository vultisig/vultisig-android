package com.vultisig.wallet.ui.screens.keysign

import android.graphics.Bitmap
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.models.peer.ConnectingToServerUiModel
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.screens.peer.PeerDiscoveryScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.forCanvasMinify
import timber.log.Timber

@Composable
internal fun KeysignPeerDiscovery(
    viewModel: KeysignFlowViewModel,
) {
    KeepScreenOn()

    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.collectAsState(initial = emptyList()).value
    val isLoading = viewModel.isLoading.collectAsState().value
    val context = LocalContext.current.applicationContext
    val sharedViewModel: KeysignShareViewModel =
        hiltViewModel(LocalActivity.current as MainActivity)
    val vault = sharedViewModel.vault ?: return
    val keysignPayload = sharedViewModel.keysignPayload
    val customMessagePayload = sharedViewModel.customMessagePayload
    val isSwap = sharedViewModel.keysignPayload?.swapPayload != null
    val amount by sharedViewModel.amount.collectAsState()
    val toAmount by sharedViewModel.toAmount.collectAsState()
    val bitmapPainter by sharedViewModel.qrBitmapPainter.collectAsState()
    val qrShareTitle = if (isSwap)
        stringResource(R.string.qr_title_join_swap_keysign)
    else
        stringResource(R.string.qr_title_join_keysign)

    val qrShareBackground = Theme.colors.oxfordBlue800

    val qrShareDescription = if (keysignPayload != null) {
        if (isSwap)
            stringResource(
                R.string.qr_title_join_keysign_swap_description,
                vault.name.forCanvasMinify(),
                amount.forCanvasMinify(),
                toAmount.forCanvasMinify(),
            ) else
            stringResource(
                R.string.qr_title_join_keysign_description,
                vault.name.forCanvasMinify(),
                amount.forCanvasMinify(),
                keysignPayload.toAddress.forCanvasMinify(),
            )
    } else ""

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
    LaunchedEffect(Unit) {
        // start mediator server
        viewModel.setData(vault, context, keysignPayload, customMessagePayload)
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

    LaunchedEffect(bitmapPainter) {
        sharedViewModel.saveShareQrBitmap(
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
        bitmapPainter = bitmapPainter,
        networkPromptOption = viewModel.networkOption.value,
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, context) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = viewModel::moveToKeysignState,
    )
}

@Composable
internal fun KeysignPeerDiscovery(
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
) {
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
            connectingToServer = if (isLookingForVultiServer) {
                ConnectingToServerUiModel(false)
            } else null,
            error = null,
        ),
        showToolbar = false,

        onBackClick = {},
        onHelpClick = {},
        onShareQrClick = {},
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
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap(),
            filterQuality = FilterQuality.None
        ),
        networkPromptOption = NetworkOption.Local,
    )
}