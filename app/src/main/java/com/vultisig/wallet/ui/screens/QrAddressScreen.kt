package com.vultisig.wallet.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.QRCodeKeyGenImage
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.QrAddressViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.extractBitmap

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@Composable
internal fun QrAddressScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<QrAddressViewModel>()
    val address = viewModel.address
    val chainName = viewModel.chainName
    val context = LocalContext.current
    val bitmapPainter by viewModel.qrBitmapPainter.collectAsState()
    val background = Theme.v2.colors.backgrounds.primary
    val title = stringResource(id = R.string.qr_address_screen_title)

    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = title,
                startIcon = R.drawable.ic_caret_left,
                endIcon = R.drawable.qr_share,
                onEndIconClick = { viewModel.shareQRCode(context) }
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(it)
                .background(background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UiSpacer(size = 42.dp)
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center,
                text = stringResource(id = R.string.qr_address_screen_description, chainName, address),
                style = Theme.menlo.body1
                    .copy(fontWeight = FontWeight.Bold),
            )
            UiSpacer(size = 42.dp)

            BoxWithConstraints(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (address.isNotEmpty()) {
                    QRCodeKeyGenImage(
                        bitmapPainter = bitmapPainter,
                        modifier = Modifier
                            .width(min(maxHeight, maxWidth))
                            .padding(all = 48.dp)
                            .aspectRatio(1f)
                            .extractBitmap { bitmap ->
                                if (bitmapPainter != null) {
                                    viewModel.saveShareQrBitmap(
                                        context,
                                        bitmap,
                                        background.toArgb(),
                                        title,
                                        BitmapFactory.decodeResource(
                                            context.resources, R.drawable.ic_share_qr_logo
                                        )
                                    )
                                } else {
                                    bitmap.recycle()
                                }
                            },
                    )
                }
            }
        }
    }
}