package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShowQrHelperBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = null,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ScanQrHelpModalContent(onDismiss)
    }
}

@Composable
private fun ScanQrHelpModalContent(onGotItClick: () -> Unit) {
    Box {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .padding(top = 7.dp, bottom = 40.dp, start = 16.dp, end = 16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.scan_qr_help),
                modifier = Modifier.width(290.dp),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
            )
            UiSpacer(32.dp)
            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_qr_code_modal_scan_the),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.scan_qr_code_modal_annotation),
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.body.s.medium,
                    textAlign = TextAlign.Center,
                )
            }
            UiSpacer(32.dp)
            VsButton(
                label = stringResource(id = R.string.scan_qr_code_modal_next),
                variant = VsButtonVariant.CTA,
                onClick = onGotItClick,
            )
        }
        HandlerLine()
    }
}

@Composable
private fun BoxScope.HandlerLine() {
    Box(
        modifier =
            Modifier.align(TopCenter)
                .padding(top = 12.dp)
                .width(36.dp)
                .height(5.dp)
                .background(color = Theme.v2.colors.border.normal, shape = CircleShape)
    )
}

@Preview
@Composable
private fun ScanQrHelpModalContentPreview() {
    ScanQrHelpModalContent {}
}
