package com.vultisig.wallet.presenter.qr_address

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.QRCodeKeyGenImage
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun QrAddressScreen(navController: NavHostController) {
    val viewmodel = hiltViewModel<QrAddressViewModel>()

    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.qr_address_screen_title),
                startIcon = R.drawable.caret_left
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(it)
                .background(Theme.colors.oxfordBlue800),
            Alignment.Center
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp
            val address = viewmodel.address!!
            val qrBoxSize = ((screenWidth * .8).coerceAtMost(300.0)).dp

            Text(
                text = address,
                style = Theme.menlo.body1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 128.dp)
            )


            val segment = with(LocalDensity.current) {
                qrBoxSize.div(5).toPx()
            }

            QRCodeKeyGenImage(
                address,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment, segment)),
                modifier = Modifier
                    .padding(all = 32.dp)
                    .fillMaxWidth(),
            )
        }
    }
}