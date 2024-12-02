package com.vultisig.wallet.ui.screens.keysign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState
import com.vultisig.wallet.ui.models.keysign.KeysignFlowViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.screens.PeerDiscoveryView
import com.vultisig.wallet.ui.screens.keygen.FastPeerDiscovery
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import com.vultisig.wallet.ui.utils.forCanvasMinify
import timber.log.Timber

@Composable
internal fun KeysignPeerDiscovery(
    viewModel: KeysignFlowViewModel,
) {
    KeepScreenOn()

    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.asFlow().collectAsState(initial = emptyList()).value
    val isLoading = viewModel.isLoading.collectAsState().value
    val context = LocalContext.current.applicationContext
    val sharedViewModel: KeysignShareViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val vault = sharedViewModel.vault ?: return
    val keysignPayload = sharedViewModel.keysignPayload ?: return
    val isSwap = sharedViewModel.keysignPayload?.swapPayload != null
    val amount by sharedViewModel.amount.collectAsState()
    val toAmount by sharedViewModel.toAmount.collectAsState()
    val bitmapPainter by sharedViewModel.qrBitmapPainter.collectAsState()
    val qrShareTitle = if (isSwap)
        stringResource(R.string.qr_title_join_swap_keysign)
    else
        stringResource(R.string.qr_title_join_keysign)

    val qrShareBackground = Theme.colors.oxfordBlue800

    val qrShareDescription = if (isSwap)
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

    LaunchedEffect(key1 = viewModel.participants) {
        viewModel.participants.asFlow().collect { newList ->
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
        viewModel.setData(vault, context, keysignPayload)
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

    KeysignPeerDiscovery(
        isLookingForVultiServer = viewModel.isFastSign &&
                Utils.getThreshold(vault.signers.size) == 2,
        selectionState = selectionState,
        participants = participants,
        bitmapPainter = bitmapPainter,
        networkPromptOption = viewModel.networkOption.value,
        hasNetworkPrompt = !viewModel.isFastSign,
        isLoading = isLoading,
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, context) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = viewModel::moveToKeysignState,
        extractBitmap = { bitmap ->
            if (bitmapPainter != null) {
                sharedViewModel.saveShareQrBitmap(
                    bitmap,
                    qrShareBackground.toArgb(),
                    qrShareTitle,
                    qrShareDescription,
                    BitmapFactory.decodeResource(
                        context.resources, R.drawable.ic_share_qr_logo
                    )
                )
            } else {
                bitmap.recycle()
            }
        }
    )
}

@Composable
internal fun KeysignPeerDiscovery(
    isLookingForVultiServer: Boolean,
    selectionState: List<String>,
    participants: List<String>,
    bitmapPainter: BitmapPainter?,
    networkPromptOption: NetworkPromptOption,
    hasNetworkPrompt: Boolean,
    isLoading: Boolean,
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
    extractBitmap: (Bitmap) -> Unit = {},
) {
    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        content = {
            if (isLookingForVultiServer) {
                FastPeerDiscovery()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                ) {
                    PeerDiscoveryView(
                        selectionState = selectionState,
                        participants = participants,
                        bitmapPainter = bitmapPainter,
                        networkPromptOption = networkPromptOption,
                        hasNetworkPrompt = hasNetworkPrompt,
                        onChangeNetwork = onChangeNetwork,
                        onAddParticipant = onAddParticipant,
                        onRemoveParticipant = onRemoveParticipant,
                        extractBitmap = extractBitmap,
                    )
                }
            }
        },
        bottomBar = {
            if (!isLookingForVultiServer) {
                MultiColorButton(
                    text = stringResource(R.string.keysign_peer_discovery_start),
                    backgroundColor = Theme.colors.turquoise600Main,
                    textColor = Theme.colors.oxfordBlue600Main,
                    minHeight = 45.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    disabled = selectionState.size < 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 16.dp,
                            horizontal = 16.dp,
                        ),
                    onClick = onStopParticipantDiscovery,
                    isLoading = isLoading,
                )
            }
        }
    )
}

@Preview
@Composable
private fun KeysignPeerDiscoveryPreview() {
    KeysignPeerDiscovery(
        isLookingForVultiServer = true,
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        bitmapPainter = BitmapPainter(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap(),
            filterQuality = FilterQuality.None
        ),
        networkPromptOption = NetworkPromptOption.LOCAL,
        hasNetworkPrompt = true,
        isLoading = false,
    )
}