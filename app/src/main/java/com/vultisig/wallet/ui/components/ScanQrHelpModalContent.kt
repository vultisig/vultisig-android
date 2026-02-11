package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShowQrHelperBottomSheet(
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        dragHandle = null,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        ScanQrHelpModalContent(onDismiss)
    }
}

@Composable
private fun ScanQrHelpModalContent(onGotItClick: () -> Unit) {
    Box {
        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.secondary)
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
                    append(stringResource(R.string.scan_qr_code_modal_scan_the))
                    append(" ")
                    withStyle(
                        style = SpanStyle(brush = Theme.v2.colors.gradients.primary)
                    ) {
                        append(stringResource(R.string.scan_qr_code_modal_qr_code))
                    }
                },
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(12.dp)
            Text(
                text = stringResource(R.string.scan_qr_code_modal_annotation),
                color = Theme.v2.colors.text.secondary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center
            )
            UiSpacer(36.dp)
            VsButton(
                modifier = Modifier
                    .fillMaxWidth(),
                label = stringResource(id = R.string.scan_qr_code_modal_next),
                onClick = onGotItClick
            )
            UiSpacer(48.dp)
        }
        HandlerLine()
    }
}


@Composable
private fun BoxScope.HandlerLine() {
    Box(
        modifier = Modifier
            .align(TopCenter)
            .padding(top = 12.dp)
            .width(64.dp)
            .height(4.dp)
            .background(
                color = Theme.v2.colors.border.normal,
                shape = CircleShape,
            )
    )
}

@Preview
@Composable
private fun ScanQrHelpModalContentPreview() {
    ScanQrHelpModalContent {}
}
