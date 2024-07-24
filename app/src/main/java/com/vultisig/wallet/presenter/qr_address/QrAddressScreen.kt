package com.vultisig.wallet.presenter.qr_address

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.QRCodeKeyGenImage
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.presenter.common.share
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun QrAddressScreen(navController: NavHostController) {
    val viewmodel = hiltViewModel<QrAddressViewModel>()
    val address = viewmodel.address!!
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.qr_address_screen_title),
                startIcon = R.drawable.caret_left,
                endIcon = R.drawable.qr_share,
                onEndIconClick = {
                    val qrBitmap = generateQrBitmap(address)
                    context.share(qrBitmap)
                }
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .background(Theme.colors.oxfordBlue800),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = address,
                style = Theme.menlo.body1
            )

            BoxWithConstraints(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val qrBoxSize = ((maxWidth.value * .8).coerceAtMost(300.0)).dp
                val segment = with(LocalDensity.current) {
                    qrBoxSize.div(5).toPx()
                }

                QRCodeKeyGenImage(
                    address,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment, segment)),
                    modifier = Modifier
                        .width(min(maxHeight, maxWidth))
                        .padding(all = 32.dp)
                        .aspectRatio(1f),
                )
            }
        }
    }
}