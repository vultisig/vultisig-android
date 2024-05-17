package com.vultisig.wallet.presenter.qr_address

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.rememberQRBitmapPainter
import com.vultisig.wallet.ui.components.QrBorder
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrAddressScreen(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens
    val viewmodel = hiltViewModel<QrAddressViewModel>()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.qr_address_screen_title),
                        style = Theme.montserrat.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier
                            .padding(
                                start = dimens.marginMedium,
                                end = dimens.marginMedium,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColor.oxfordBlue800,
                    titleContentColor = textColor
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "back", tint = Color.White
                        )
                    }
                },
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
            val imageAndBorderSpace = 30.dp
            val imageSizeInDp = qrBoxSize - 2 * imageAndBorderSpace
            val whiteSpace = 10.dp

            Text(
                text = address,
                style = Theme.menlo.body1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 128.dp)
            )

            Box(
                Modifier
                    .clip(shape = RoundedCornerShape(qrBoxSize.div(10)))
                    .size(qrBoxSize)
                    .background(Theme.colors.oxfordBlue600Main),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(imageSizeInDp + whiteSpace)
                        .clip(RoundedCornerShape(qrBoxSize.div(20)))
                        .background(Theme.colors.neutral0),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium),
                        painter = rememberQRBitmapPainter(
                            qrCodeContent = address,
                            imageWidthInDp = imageSizeInDp,
                            imageHeightInDp = imageSizeInDp
                        ),
                        contentScale = ContentScale.FillBounds,
                        contentDescription = "coin address",
                    )
                }

                QrBorder(size = qrBoxSize, borderWidth = 6f)
            }
        }
    }
}