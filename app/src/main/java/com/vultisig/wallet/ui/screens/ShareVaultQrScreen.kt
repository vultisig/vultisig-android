package com.vultisig.wallet.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ShareVaultQrViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.WriteFilePermissionHandler
import com.vultisig.wallet.ui.utils.extractBitmap


@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    viewModel: ShareVaultQrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val qrBitmapPainter by viewModel.qrBitmapPainter.collectAsState()

    val context = LocalContext.current
    val mainColor = Theme.colors.neutral0
    val backgroundColor = Theme.colors.transparent

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) WriteFilePermissionHandler(
        viewModel.permissionFlow, viewModel::onPermissionResult
    )

    LaunchedEffect(state.shareVaultQrString) {
        viewModel.loadQrCodePainter(
            mainColor = mainColor,
            backgroundColor = backgroundColor,
            logo = BitmapFactory.decodeResource(
                context.resources, R.drawable.ic_qr_vultisig
            )
        )
    }

    LaunchedEffect(state.fileUri) {
        state.fileUri?.let {
            shareQr(it, context)
        }
    }

    ShareVaultQrScreen(
        navController = navController,
        uid = state.shareVaultQrModel.uid,
        name = state.shareVaultQrModel.name,
        qrBitmapPainter = qrBitmapPainter,
        shareVaultQrString = state.shareVaultQrString,
        saveShareQrBitmap = viewModel::saveShareQrBitmap,
        onShareButtonClicked = {
            if (state.fileUri == null) {
                viewModel.share()
            } else {
                shareQr(requireNotNull(state.fileUri), context)
            }
        },
        onSaveButtonClicked = {
            if (state.fileUri == null) {
                viewModel.save()
            } else {
                viewModel.showSnackbarSavedMessage()
                navController.popBackStack()
            }
        },
    )
}

@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    uid: String,
    name: String,
    qrBitmapPainter: BitmapPainter?,
    shareVaultQrString: String?,
    saveShareQrBitmap: (Bitmap) -> Unit,
    onShareButtonClicked: () -> Unit,
    onSaveButtonClicked: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {

            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
            ) {
                MultiColorButton(
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.oxfordBlue800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = null,
                                tint = Theme.colors.oxfordBlue800,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.share_vault_qr_share),
                                color = Theme.colors.oxfordBlue800,
                                style = Theme.montserrat.subtitle1
                            )
                        }
                    },
                    onClick = onShareButtonClicked,
                )

                UiSpacer(size = 12.dp)

                MultiColorButton(
                    text = stringResource(R.string.share_vault_qr_save),
                    backgroundColor = Theme.colors.oxfordBlue800,
                    textColor = Theme.colors.turquoise800,
                    iconColor = Theme.colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSaveButtonClicked,
                )

            }
        },
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.share_vault_qr_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) {

        val modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 32.dp, vertical = 4.dp
            )
            .extractBitmap { bitmap ->
                if (qrBitmapPainter != null) {
                    saveShareQrBitmap(bitmap)
                } else {
                    bitmap.recycle()
                }
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(it),
            contentAlignment = Alignment.Center,
        ) {
            QrCard(shareVaultQrString, qrBitmapPainter, name, uid, modifier)
        }
    }
}

@Composable
private fun QrCard(
    shareVaultQrString: String?,
    qrBitmapPainter: BitmapPainter?,
    name: String,
    uid: String,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Theme.colors.aquamarine,
                            Theme.colors.sapphireGlitter,
                        ),
                    ),
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                style = Theme.menlo.heading5.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Theme.colors.neutral0,
            )
            UiSpacer(4.dp)
            Text(
                text = stringResource(R.string.share_vault_qr_uid, uid),
                style = Theme.menlo.subtitle1.copy(
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.Center,
                color = Theme.colors.neutral0,
            )
            UiSpacer(8.dp)

            if (shareVaultQrString == null || qrBitmapPainter == null) {
                Box(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth()
                        .background(Theme.colors.neutral0),
                ) {}
            } else {
                Image(
                    painter = qrBitmapPainter,
                    contentDescription = "qr",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                )
            }
            UiSpacer(8.dp)
            Text(
                text = stringResource(R.string.share_vault_qr_address),
                style = Theme.menlo.subtitle3.copy(
                    fontSize = 18.sp,
                ),
                color = Theme.colors.neutral0,
            )
            UiSpacer(8.dp)
        }
    }
}


private fun shareQr(
    fileUri: Uri, context: Context
) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.setType("image/png")
    val shareIntent = Intent.createChooser(intent, null)
    context.startActivity(shareIntent)
}

@Preview
@Composable
internal fun ShareVaultQrScreenPreview() {
    ShareVaultQrScreen(
        navController = rememberNavController(),
        uid = "8273647823saresfgaerfgsbgsetbgsetgbsgtbsbvsrvaev",
        name = "Main Vault",
        shareVaultQrString = "placeholder",
        qrBitmapPainter = BitmapPainter(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        ),
        saveShareQrBitmap = {},
        onShareButtonClicked = {},
        onSaveButtonClicked = {},
    )
}