package com.vultisig.wallet.ui.screens.keygen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.components.vultiGradientV2
import com.vultisig.wallet.ui.models.keygen.KeygenFlowViewModel
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.models.keygen.VaultSetupType.Companion.asString
import com.vultisig.wallet.ui.screens.PeerDiscoveryView
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import kotlinx.coroutines.launch

@Composable
internal fun KeygenPeerDiscovery(
    navController: NavHostController,
    viewModel: KeygenFlowViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val qrShareTitle = stringResource(R.string.qr_title_join_keygen)
    val qrShareBackground = Theme.colors.oxfordBlue800
    val qrShareDescription = stringResource(R.string.qr_title_join_keygen_description)

    KeygenPeerDiscoveryScreen(
        navController = navController,
        isLookingForVultiServer = uiState.vaultSetupType == VaultSetupType.FAST,
        hasNetworkPrompt = uiState.vaultSetupType == VaultSetupType.SECURE,
        selectionState = uiState.selection,
        isReshare = uiState.isReshareMode,
        participants = uiState.participants,
        bitmapPainter = uiState.qrBitmapPainter,
        vaultSetupType = uiState.vaultSetupType.asString(),
        networkPromptOption = uiState.networkOption,
        isContinueEnabled = uiState.isContinueButtonEnabled,
        onQrAddressClick = { viewModel.shareQRCode(context) },
        onChangeNetwork = viewModel::changeNetworkPromptOption,
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        extractBitmap = { bitmap ->
            if (uiState.qrBitmapPainter != null) {
                viewModel.saveShareQrBitmap(
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
        },
        onStopParticipantDiscovery = {
            viewModel.finishPeerDiscovery()
        },
        isLoading = uiState.isLoading,
        onDismissModal = viewModel::saveHelperModalVisited,
        isQrHelpModalVisited = uiState.isQrHelpModalVisited
    )
}

@Composable
internal fun KeygenPeerDiscoveryScreen(
    navController: NavHostController,
    isLookingForVultiServer: Boolean,
    hasNetworkPrompt: Boolean,
    selectionState: List<String>,
    participants: List<String>,
    bitmapPainter: BitmapPainter?,
    vaultSetupType: String,
    networkPromptOption: NetworkPromptOption,
    isContinueEnabled: Boolean,
    onQrAddressClick: () -> Unit = {},
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
    extractBitmap: (Bitmap) -> Unit,
    isReshare: Boolean,
    isLoading: Boolean,
    onDismissModal: () -> Unit,
    isQrHelpModalVisited: Boolean,
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
        endIcon = R.drawable.qr_share.takeIf { !isLookingForVultiServer },
        onEndIconClick = onQrAddressClick.takeIf { !isLookingForVultiServer } ?: {},
    ) {
        if (isLookingForVultiServer) {
            FastPeerDiscovery()
        } else {
            if(!isQrHelpModalVisited)
                ShowHelperDialog(onDismissModal)
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {

                PeerDiscoveryView(
                    modifier = Modifier.weight(1f),
                    selectionState = selectionState,
                    participants = participants,
                    bitmapPainter = bitmapPainter,
                    hasNetworkPrompt = hasNetworkPrompt,
                    networkPromptOption = networkPromptOption,
                    onChangeNetwork = onChangeNetwork,
                    onAddParticipant = onAddParticipant,
                    onRemoveParticipant = onRemoveParticipant,
                    extractBitmap = extractBitmap
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
                    isLoading = isLoading,
                )
            }
        }
    }
}

@Composable
internal fun FastPeerDiscovery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = CenterHorizontally,
    ) {
        UiSpacer(size = 74.dp)

        Text(
            text = stringResource(R.string.keygen_vultiserver_peer_discovery_waiting),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        )

        UiSpacer(size = 48.dp)

        UiCirclesLoader()
        UiSpacer(size = 48.dp)
        Text(
            text = stringResource(R.string.keygen_vultiserver_peer_discovery_please_wait),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.body2
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowHelperDialog(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        containerColor = Theme.colors.oxfordBlue600Main,
        shape = RectangleShape,
        dragHandle = null,
        sheetState = sheetState,
        onDismissRequest = {
            coroutineScope.launch {
                sheetState.hide()
            }
        }
    ) {
        ScanQrHelpModalBottomSheet {
            coroutineScope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
}


@Composable
private fun ScanQrHelpModalBottomSheet(onGotItClick: () -> Unit) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.oxfordBlue600Main)
            .padding(horizontal = 35.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.scan_qr_help),
            modifier = Modifier
                .width(290.dp),
            contentDescription = null,
            contentScale = ContentScale.FillWidth
        )
        UiSpacer(36.dp)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.scan_qr_code_screen_scan_the))
                append(" ")
                withStyle(
                    style = SpanStyle(brush = Brush.vultiGradientV2())
                ) {
                    append(stringResource(R.string.scan_qr_code_screen_qr_code))
                }
            },
            style = Theme.brockmann.headings.title2,
            color = Theme.colors.text.primary,
        )
        UiSpacer(12.dp)
        Text(
            text = stringResource(R.string.scan_qr_code_annotation),
            color = Theme.colors.text.light,
            style = Theme.brockmann.body.s.medium,
            textAlign = TextAlign.Center
        )
        UiSpacer(36.dp)
        VsButton(
            modifier = Modifier
                .fillMaxWidth(),
            label = stringResource(id = R.string.scan_qr_screen_next),
            onClick = onGotItClick
        )
        UiSpacer(48.dp)
    }
}

@Preview
@Composable
private fun KeygenPeerDiscoveryScreenPreview() {
    KeygenPeerDiscoveryScreen(
        navController = rememberNavController(),
        isLookingForVultiServer = false,
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        bitmapPainter = BitmapPainter(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap(),
            filterQuality = FilterQuality.None
        ),
        networkPromptOption = NetworkPromptOption.LOCAL,
        isContinueEnabled = true,
        vaultSetupType = "M/N",
        isReshare = true,
        extractBitmap = {},
        hasNetworkPrompt = true,
        isLoading = false,
        isQrHelpModalVisited = true,
        onDismissModal = {}
    )
}
