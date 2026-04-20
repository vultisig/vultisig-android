package com.vultisig.wallet.ui.screens.keysign

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.usecases.QrShareField
import com.vultisig.wallet.data.usecases.QrShareInfo
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
    sharedViewModel: KeysignShareViewModel = hiltViewModel(LocalActivity.current as MainActivity),
) {
    KeepScreenOn()

    val activity = LocalContext.current
    val uiModel by viewModel.uiState.collectAsState()

    val selectionState by viewModel.selection.collectAsState()
    val participants = viewModel.participants.collectAsState(initial = emptyList()).value
    val isDataLoaded by viewModel.isDataLoaded.collectAsState()
    val keysignMessage by viewModel.keysignMessage.collectAsState()
    val networkOption by viewModel.networkOption.collectAsState()
    val context = LocalContext.current.applicationContext

    LaunchedEffect(txType) {
        viewModel.setData(shareViewModel = sharedViewModel, context = context, txType = txType)
    }

    val isSwap = uiModel.isSwap
    val qrShareTitle =
        if (isSwap) stringResource(R.string.qr_title_join_swap_keysign)
        else stringResource(R.string.qr_title_join_keysign)

    val qrShareBackground = Theme.v2.colors.backgrounds.primary

    val vault = uiModel.vault
    val labelVault = stringResource(R.string.qr_share_label_vault)
    val labelAmount = stringResource(R.string.qr_share_label_amount)
    val labelTo = stringResource(R.string.qr_share_label_to)
    val labelFrom = stringResource(R.string.qr_share_label_from)
    val srcIconBitmap =
        remember(uiModel.srcTokenLogoRes) {
            uiModel.srcTokenLogoRes?.let { BitmapFactory.decodeResource(context.resources, it) }
        }
    val dstIconBitmap =
        remember(uiModel.dstTokenLogoRes) {
            uiModel.dstTokenLogoRes?.let { BitmapFactory.decodeResource(context.resources, it) }
        }
    val vultisigLogoBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.logo)
    }
    val qrShareInfo =
        QrShareInfo(
            title = qrShareTitle,
            fields =
                if (isSwap)
                    listOf(
                        QrShareField(labelVault, vault.name.forCanvasMinify()),
                        QrShareField(labelFrom, uiModel.amount.forCanvasMinify(), srcIconBitmap),
                        QrShareField(labelTo, uiModel.toAmount.forCanvasMinify(), dstIconBitmap),
                    )
                else
                    listOf(
                        QrShareField(labelVault, vault.name.forCanvasMinify()),
                        QrShareField(labelAmount, uiModel.amount.forCanvasMinify(), srcIconBitmap),
                        QrShareField(labelTo, uiModel.toAddress.forCanvasMinify()),
                    ),
        )

    LaunchedEffect(key1 = viewModel.participants) {
        viewModel.participants.collect { newList ->
            // add all participants to the selection
            for (participant in newList) {
                viewModel.addParticipant(participant)
            }
        }
    }
    LaunchedEffect(key1 = viewModel.selection, vault, isDataLoaded) {
        viewModel.selection.collect { newList ->
            if (!isDataLoaded) return@collect
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

    LaunchedEffect(keysignMessage) {
        if (keysignMessage.isNotEmpty()) {
            sharedViewModel.loadQrPainter(keysignMessage)
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.stopParticipantDiscovery() } }

    LaunchedEffect(uiModel.qrBitmapPainter, qrShareInfo) {
        sharedViewModel.saveShareQrBitmap(
            context,
            qrShareBackground.toArgb(),
            qrShareInfo,
            vultisigLogoBitmap,
        )
    }
    val isLookingForVultiServer =
        viewModel.isFastSign && Utils.getThreshold(vault.signers.size) == 2
    if (isLookingForVultiServer) {
        ConnectingToServer(false)
    } else {
        val minimumDevices = Utils.getThreshold(vault.signers.size)
        PeerDiscoveryScreen(
            state =
                PeerDiscoveryUiModel(
                    qr = uiModel.qrBitmapPainter,
                    network = networkOption,
                    localPartyId = vault.localPartyID,
                    devices = participants.filter { it != vault.localPartyID },
                    selectedDevices = selectionState.filter { it != vault.localPartyID },
                    minimumDevices = minimumDevices,
                    minimumDevicesDisplayed = minimumDevices,
                    showQrHelpModal = false,
                    showDevicesHint = false,
                    connectingToServer = null,
                    error = null,
                    enableNotification = uiModel.enableNotification,
                    resendCooldownSeconds = uiModel.resendCooldownSeconds,
                ),
            onBackClick = viewModel::back,
            showHelp = false,
            onHelpClick = {},
            onShareQrClick = { sharedViewModel.shareQRCode(activity) },
            onCloseHintClick = {},
            onDismissQrHelpModal = {},
            onSwitchModeClick = {
                viewModel.changeNetworkPromptOption(
                    when (networkOption) {
                        NetworkOption.Internet -> NetworkOption.Local
                        NetworkOption.Local -> NetworkOption.Internet
                    },
                    context,
                )
            },
            onDeviceClick = viewModel::handleParticipant,
            onNextClick = viewModel::moveToKeysignState,
            onResendNotification = viewModel::sendNotification,
        )
    }
}

@Preview
@Composable
private fun KeysignPeerDiscoveryPreview() {
    val selectionState = listOf("1", "2")
    PeerDiscoveryScreen(
        state =
            PeerDiscoveryUiModel(
                qr =
                    BitmapPainter(
                        createBitmap(1, 1).asImageBitmap(),
                        filterQuality = FilterQuality.None,
                    ),
                network = NetworkOption.Internet,
                localPartyId = "1",
                devices = listOf("1", "2", "3").filter { it != "1" },
                selectedDevices = selectionState.filter { it != "1" },
                minimumDevices = 2,
                minimumDevicesDisplayed = 2,
                showQrHelpModal = false,
                showDevicesHint = false,
                connectingToServer = null,
                error = null,
                enableNotification = true,
            ),
        onBackClick = {},
        showHelp = false,
        onHelpClick = {},
        onShareQrClick = {},
        onCloseHintClick = {},
        onDismissQrHelpModal = {},
        onSwitchModeClick = {},
        onDeviceClick = {},
        onNextClick = {},
        onResendNotification = {},
    )
}
