package com.vultisig.wallet.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.ShareVaultQrViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.WriteFilePermissionHandler


@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    viewModel: ShareVaultQrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val qrBitmapPainter by viewModel.qrBitmapPainter.collectAsState()
    val picture = remember { Picture() }

    val context = LocalContext.current
    val mainColor = Theme.colors.neutral0
    val backgroundColor = Theme.colors.transparent
    val shareBackgroundColor = Theme.colors.tallShips

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) WriteFilePermissionHandler(
        viewModel.permissionFlow, viewModel::onPermissionResult
    )

    LaunchedEffect(state.shareVaultQrString) {
        viewModel.loadQrCode(
            mainColor = mainColor,
            backgroundColor = backgroundColor,
            shareBackgroundColor = shareBackgroundColor,
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
        picture = picture,
        ecdsa = state.shareVaultQrModel.publicKeyEcdsa,
        eddsa = state.shareVaultQrModel.publicKeyEddsa,
        name = state.shareVaultQrModel.name,
        qrBitmapPainter = qrBitmapPainter,
        shareVaultQrString = state.shareVaultQrString,
        onButtonClicked = {
            if (state.fileUri == null) {
                viewModel.onSaveClicked(picture)
            } else {
                shareQr(requireNotNull(state.fileUri), context)
            }
        },
    )
}

@Composable
internal fun ShareVaultQrScreen(
    navController: NavController,
    ecdsa: String,
    eddsa: String,
    name: String,
    picture: Picture,
    qrBitmapPainter: BitmapPainter?,
    shareVaultQrString: String?,
    onButtonClicked: () -> Unit,
) {
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

        val modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 32.dp,
                vertical = 4.dp
            )
            .drawWithCache {
                val width = this.size.width.toInt()
                val height = this.size.height.toInt()
                onDrawWithContent {
                    val pictureCanvas =
                        androidx.compose.ui.graphics.Canvas(
                            picture.beginRecording(
                                width,
                                height
                            )
                        )
                    draw(this, this.layoutDirection, pictureCanvas, this.size) {
                        this@onDrawWithContent.drawContent()
                    }
                    picture.endRecording()

                    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
                }
            }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = Alignment.Center,
        ) {
            val configuration = LocalConfiguration.current
            when (configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    HorizontalView(shareVaultQrString, qrBitmapPainter, name, ecdsa, eddsa, modifier)
                else ->
                    VerticalView(shareVaultQrString, qrBitmapPainter, name, ecdsa, eddsa, modifier)
            }
        }
    }
}

@Composable
private fun VerticalView(
    shareVaultQrString: String?,
    qrBitmapPainter: BitmapPainter?,
    name: String,
    ecdsa: String,
    eddsa: String,
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

            Text(
                text = name,
                style = Theme.menlo.heading5.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Theme.colors.neutral0,
            )
            Text(
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                text = stringResource(R.string.share_vault_qr_card_keys, ecdsa, eddsa),
                style = Theme.montserrat.body1.copy(
                    fontSize = 10.sp,
                ),
                color = Theme.colors.neutral0,
            )
        }
    }
}

@Composable
private fun HorizontalView(
    shareVaultQrString: String?,
    qrBitmapPainter: BitmapPainter?,
    name: String,
    ecdsa: String,
    eddsa: String,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Theme.colors.aquamarine,
                            Theme.colors.sapphireGlitter,
                        ),
                    ),
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shareVaultQrString == null || qrBitmapPainter == null) {
                Box(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxSize()
                        .background(Theme.colors.neutral0),
                ) {}
            } else {
                Image(
                    painter = qrBitmapPainter,
                    contentDescription = "qr",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight()
                        .aspectRatio(1f),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            )

            {
                Text(
                    text = name,
                    style = Theme.menlo.heading5.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Theme.colors.neutral0,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
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
        picture = Picture(),
        ecdsa = "placeholderdjhfkajsdhflkajshflkasdjflkajsdflk",
        eddsa = "placeholderdjhfkajsdhflkajshflkasdjflkajsdflk",
        name = "Main Vault",
        shareVaultQrString = "placeholder",
        qrBitmapPainter = BitmapPainter(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        ),
        onButtonClicked = {},
    )
}