package com.vultisig.wallet.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.rememberQRBitmapPainter
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.ShareVaultQrViewModel
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    viewModel: ShareVaultQrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ShareVaultQrScreen(
        navController = navController,
        ecdsa = state.shareVaultQrModel.public_key_ecdsa,
        eddsa = state.shareVaultQrModel.public_key_eddsa,
        shareVaultQrString = state.shareVaultQrString,
        onButtonClicked = {
            viewModel.onSaveOrShareClicked()
        },
    )
}

@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    ecdsa: String,
    eddsa: String,
    shareVaultQrString: String?,
    onButtonClicked: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            MultiColorButton(
                text = stringResource(R.string.share_vault_qr_save_or_share),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                onClick = onButtonClicked,
            )
        },
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.share_vault_qr_title),
                startIcon = R.drawable.caret_left,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF33E6BF),
                                    Color(0xFF0439C7),
                                ),
                            ),
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ){
                    if (shareVaultQrString == null) {
                        Box(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth()
                                .background(Theme.colors.neutral0),
                        ) {}
                    }else{
                        Image(
                            painter = rememberQRBitmapPainter(
                                qrCodeContent = shareVaultQrString,
                                mainColor = Theme.colors.neutral0,
                                backgroundColor = Theme.colors.transparent,
                                logo = BitmapFactory.decodeResource(
                                    context.resources,
                                    R.drawable.ic_qr_vultisig
                                )
                            ),
                            contentDescription = "qr",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                        )
                    }

                    Text(
                        text = stringResource(R.string.share_vault_qr_title),
                        style = Theme.menlo.heading5.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Theme.colors.neutral0,
                    )
                    Text(
                        modifier = Modifier
                            .padding( top = 16.dp, bottom = 32.dp),
                        text = stringResource(R.string.share_vault_qr_card_keys, ecdsa, eddsa),
                        style = Theme.montserrat.body1.copy(
                            fontSize = 10.sp,
                        ),
                        color = Theme.colors.neutral0,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
internal fun ShareVaultQrScreenPreview() {
    ShareVaultQrScreen(
        navController = rememberNavController(),
        ecdsa = "placeholderdjhfkajsdhflkajshflkasdjflkajsdflk",
        eddsa = "placeholderdjhfkajsdhflkajshflkasdjflkajsdflk",
        shareVaultQrString = "placeholder",
        onButtonClicked = {},
    )
}