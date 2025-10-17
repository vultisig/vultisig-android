package com.vultisig.wallet.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
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
        uid = state.shareVaultQrModel.uid,
        name = state.shareVaultQrModel.name,
        qrBitmapPainter = qrBitmapPainter,
        shareVaultQrString = state.shareVaultQrString,
        onBackClick = navController::popBackStack,
        saveShareQrBitmap = viewModel::saveShareQrBitmap,
        onShareClick = {
            if (state.fileUri == null) {
                viewModel.share()
            } else {
                shareQr(requireNotNull(state.fileUri), context)
            }
        },
        onSaveClick = {
            if (state.fileUri == null) {
                viewModel.save()
            } else {
                viewModel.showSnackbarSavedMessage()
                navController.popBackStack()
            }
        }
    )
}


private fun shareQr(
    fileUri: Uri, context: Context
) {
    val intent = Intent(Intent.ACTION_SEND)
    intent.putExtra(Intent.EXTRA_STREAM, fileUri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.type = "image/png"
    val shareIntent = Intent.createChooser(intent, null)
    context.startActivity(shareIntent)
}


@Composable
internal fun ShareVaultQrScreen(
    uid: String,
    name: String,
    qrBitmapPainter: BitmapPainter?,
    shareVaultQrString: String?,
    saveShareQrBitmap: (Bitmap) -> Unit,
    onBackClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.share_vault_qr_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
            )
        },
        containerColor = Theme.colors.backgrounds.primary,
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(
                        vertical = 16.dp,
                        horizontal = 12.dp,
                    )
            ) {
                Info()

                UiSpacer(8.dp)
                VsButton(
                    label = stringResource(R.string.share_vault_qr_share),
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth(),
                    size = VsButtonSize.Small,
                    variant = VsButtonVariant.Primary
                )

                UiSpacer(12.dp)

                VsButton(
                    label = stringResource(R.string.share_vault_qr_save),
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    size = VsButtonSize.Medium,
                    variant = VsButtonVariant.Secondary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
        ) {

            UiSpacer(
                size = 48.dp
            )

            // `verticalScroll(rememberScrollState())` is required to preserve share and save functionality
            QrContainer(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(
                        all = 32.dp,
                    )
                    .extractBitmap { bitmap ->
                        if (qrBitmapPainter != null) {
                            saveShareQrBitmap(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    },
                uid = uid,
                name = name,
                qrBitmapPainter = qrBitmapPainter,
                shareVaultQrString = shareVaultQrString,
            )

        }
    }
}

@Composable
private fun QrContainer(
    modifier: Modifier = Modifier,
    uid: String,
    name: String,
    qrBitmapPainter: BitmapPainter?,
    shareVaultQrString: String?,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(
                    size = 24.dp
                )
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(
                    size = 24.dp
                )
            )
            .padding(
                horizontal = 36.dp,
                vertical = 24.dp
            ),
        horizontalAlignment = CenterHorizontally,

        ) {

        Text(
            text = name,
            style = Theme.brockmann.body.m.medium,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center
        )

        UiSpacer(
            size = 8.dp
        )

        Text(
            text = stringResource(
                R.string.share_vault_qr_uid,
                uid.takeLast(10)
            ), /*Should we display the full text, or only the last 10 items, based on the UI design??*/
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.light,
        )

        UiSpacer(
            size = 12.dp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = RoundedCornerShape(
                        size = 28.dp,
                    )
                )
                .padding(30.dp)
        ) {
            if (qrBitmapPainter != null && !shareVaultQrString.isNullOrBlank()) {
                Image(
                    painter = qrBitmapPainter,
                    contentDescription = "qr",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        UiSpacer(
            size = 12.dp
        )

        Text(
            text = stringResource(R.string.share_vault_qr_address),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.light,
        )

    }
}

@Composable
private fun Info() {
    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(
                    size = 12.dp
                )
            )
            .background(
                shape = RoundedCornerShape(
                    size = 12.dp
                ),
                color = Theme.colors.backgrounds.neutral
            )
            .padding(
                all = 16.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_info,
            size = 16.dp,
            tint = Theme.colors.text.light,
        )
        Text(
            text = stringResource(R.string.share_vault_qr_info),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.light,
        )
    }
}


@Preview
@Composable
private fun ShareVaultQrCardPreview() {
    ShareVaultQrScreen(
        uid = "8273647823saresfgaerfgsbgsetbgsetgbsgtbsbvsrvaev",
        name = "Main Vault",
        qrBitmapPainter = BitmapPainter(
            createBitmap(1, 1).asImageBitmap()
        ),
        shareVaultQrString = null,
        saveShareQrBitmap = {}
    )
}