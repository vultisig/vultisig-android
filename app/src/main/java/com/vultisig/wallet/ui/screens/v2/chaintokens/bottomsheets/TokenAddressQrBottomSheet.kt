package com.vultisig.wallet.ui.screens.v2.chaintokens.bottomsheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.bottomsheet.DottyBottomSheet
import com.vultisig.wallet.ui.models.TokenAddressQrViewModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.QrContainer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun TokenAddressQrBottomSheet(
    viewModel: TokenAddressQrViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

    TokenAddressQrBottomSheet(
        chainName = uiState.chainName,
        chainAddress = uiState.chainAddress,
        qrBitmapPainter = uiState.qrCode,
        graphicsLayer = graphicsLayer,
        onDismiss = viewModel::back,
        onBottomSheetExpanded = viewModel::loadData,
        onShareQrClick = {
            viewModel.shareQRCode(
                graphicsLayer = graphicsLayer,
                context = context
            )
        },
        onCopyAddressClick = {
            VsClipboardService.copy(
                context,
                uiState.chainAddress
            )
            viewModel.copy()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenAddressQrBottomSheet(
    chainName: String,
    chainAddress: String,
    qrBitmapPainter: BitmapPainter?,
    graphicsLayer: GraphicsLayer,
    onBottomSheetExpanded: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onShareQrClick: () -> Unit = {},
    onCopyAddressClick: () -> Unit = {},
) {

    DottyBottomSheet(
        onExpand = onBottomSheetExpanded,
        onDismiss = onDismiss
    ) {
        TokenAddressQrContent(
            chainName = chainName,
            qrBitmapPainter = qrBitmapPainter,
            graphicsLayer = graphicsLayer,
            chainAddress = chainAddress,
            onShareQrClick = onShareQrClick,
            onCopyAddressClick = onCopyAddressClick
        )
    }

}

@Composable
private fun TokenAddressQrContent(
    chainName: String,
    qrBitmapPainter: BitmapPainter?,
    graphicsLayer: GraphicsLayer,
    chainAddress: String,
    onShareQrClick: () -> Unit,
    onCopyAddressClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        )  {
            QrContainer(
                chainName = chainName,
                qrCode = qrBitmapPainter
            )

            UiSpacer(
                size = 24.dp
            )

            Text(
                text = chainAddress,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.colors.text.primary,
                modifier = Modifier.width(232.dp),
                textAlign = TextAlign.Center
            )
        }

        Row {
            VsButton(
                variant = VsButtonVariant.Tertiary,
                size = VsButtonSize.Small,
                label = stringResource(R.string.share_vault_qr_share),
                onClick = onShareQrClick,
                modifier = Modifier
                    .weight(1f)
            )
            UiSpacer(
                size = 4.dp
            )
            VsButton(
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Small,
                label = stringResource(R.string.copy_address),
                onClick = onCopyAddressClick,
                modifier = Modifier
                    .weight(1f)
            )

        }

        UiSpacer(
            size = 24.dp
        )
    }
}

@Preview
@Composable
private fun TokenAddressQrContentPreview() {
    TokenAddressQrContent(
        chainName = "Ethereum",
        chainAddress = "0x0000000000000000000000000000000000000000",
        graphicsLayer = rememberGraphicsLayer(),
        qrBitmapPainter = null,
        onShareQrClick = {},
        onCopyAddressClick = {}
    )
}