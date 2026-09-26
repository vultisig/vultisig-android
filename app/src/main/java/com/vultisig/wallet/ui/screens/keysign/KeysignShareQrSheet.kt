package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.GenerateQrBitmapImpl
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.bottomsheets.DottyBottomSheet
import com.vultisig.wallet.ui.models.keysign.KeysignShareViewModel
import com.vultisig.wallet.ui.screens.peer.PairingQrFrame
import com.vultisig.wallet.ui.theme.Theme

/**
 * Lets the initiator of a secure sign hand the pairing QR to another device, either as the join
 * link it encodes or as the rendered QR image.
 */
@Composable
internal fun KeysignShareQrSheet(viewModel: KeysignShareViewModel, onDismiss: () -> Unit) {
    // Taken outside the sheet: the sheet composes in its own dialog window, and the share chooser
    // has to be started from the activity.
    val context = LocalContext.current
    val qrCode by viewModel.qrBitmapPainter.collectAsState()

    KeysignShareQrSheet(
        qrCode = qrCode,
        onDismiss = onDismiss,
        onCopyLinkClick = {
            viewModel.copyQrLink(context)
            onDismiss()
        },
        onShareImageClick = {
            viewModel.shareQRCode(context)
            onDismiss()
        },
    )
}

@Composable
private fun KeysignShareQrSheet(
    qrCode: BitmapPainter?,
    onDismiss: () -> Unit,
    onCopyLinkClick: () -> Unit,
    onShareImageClick: () -> Unit,
) {
    DottyBottomSheet(onDismiss = onDismiss) {
        KeysignShareQrContent(
            qrCode = qrCode,
            onCopyLinkClick = onCopyLinkClick,
            onShareImageClick = onShareImageClick,
        )
    }
}

@Composable
private fun KeysignShareQrContent(
    qrCode: BitmapPainter?,
    onCopyLinkClick: () -> Unit,
    onShareImageClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp)
                .widthIn(max = 326.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Text(
            text = stringResource(R.string.keysign_share_qr_title),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        PairingQrFrame(qrCode = qrCode, modifier = Modifier.width(224.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VsButton(
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Medium,
                label = stringResource(R.string.keysign_share_qr_copy_link),
                onClick = onCopyLinkClick,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Medium,
                label = stringResource(R.string.keysign_share_qr_share_image),
                onClick = onShareImageClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview
@Composable
private fun KeysignShareQrContentPreview() {
    val qr =
        GenerateQrBitmapImpl()(
            "https://vultisig.com?type=SignTransaction&vault=preview",
            Color.White.toArgb(),
            Color.Transparent.toArgb(),
            null,
        )
    KeysignShareQrContent(
        qrCode = BitmapPainter(qr.asImageBitmap(), filterQuality = FilterQuality.None),
        onCopyLinkClick = {},
        onShareImageClick = {},
    )
}
