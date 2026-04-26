@file:OptIn(ExperimentalPermissionsApi::class)

package com.vultisig.wallet.ui.screens.scan

import android.Manifest
import android.content.Intent
import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.banners.BannerVariant
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.utils.roundToPx
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.components.v3.V3Topbar
import com.vultisig.wallet.ui.models.ScanQrUiModel
import com.vultisig.wallet.ui.models.ScanQrViewModel
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.addWhiteBorder
import com.vultisig.wallet.ui.utils.setupCamera
import com.vultisig.wallet.ui.utils.unbindCameraListener
import com.vultisig.wallet.ui.utils.uriToBitmap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanQrScreen(viewModel: ScanQrViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = viewModel::back,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        scrimColor = Theme.v2.colors.backgrounds.primary,
    ) {
        ScanQrScreen(
            uiModel = uiModel,
            onDismiss = viewModel::back,
            onScanSuccess = viewModel::process,
            onError = viewModel::handleError,
            onMoreInfo = viewModel::toggleShowTip,
        )
    }
}

@Composable
private fun ScanQrScreen(
    onDismiss: () -> Unit,
    onScanSuccess: (qr: String) -> Unit,
    uiModel: ScanQrUiModel,
    onError: (String) -> Unit = {},
    onMoreInfo: () -> Unit,
) {
    var isFrameHighlighted by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanned by remember { mutableStateOf(false) }
    var isPickerLaunched by remember { mutableStateOf(false) }
    var hasRequestedPermission by remember { mutableStateOf(false) }

    val onSuccess: (List<Barcode>) -> Unit = { barcodes ->
        if (barcodes.isNotEmpty()) {
            if (isScanned.not()) {
                isScanned = true
                val barcode = barcodes.first()
                val barcodeValue = barcode.rawValue
                Timber.d(context.getString(R.string.successfully_scanned_barcode, barcodeValue))
                if (barcodeValue != null) {
                    onScanSuccess(barcodeValue)
                }
            }
        } else {
            onError(context.getString(R.string.no_barcodes_found))
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) { onDispose { executor.shutdownNow() } }

    val pickMedia =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            isPickerLaunched = false
            coroutineScope.launch {
                if (uri != null) {
                    try {
                        val first = scanImage(InputImage.fromFilePath(context, uri))
                        val firstBarcodes = (first as? ScanResult.Success)?.barcodes.orEmpty()
                        if (firstBarcodes.isNotEmpty()) {
                            onSuccess(firstBarcodes)
                        } else {
                            val bitmap =
                                requireNotNull(uriToBitmap(context.contentResolver, uri))
                                    .addWhiteBorder(2F)
                            val retry = scanImage(InputImage.fromBitmap(bitmap, 0))
                            bitmap.recycle()
                            when (retry) {
                                is ScanResult.Success ->
                                    if (retry.barcodes.isNotEmpty()) {
                                        onSuccess(retry.barcodes)
                                    } else {
                                        onError(context.getString(R.string.no_barcodes_found))
                                    }
                                is ScanResult.Failure -> {
                                    Timber.e(
                                        "Scan failed: %s",
                                        (first as? ScanResult.Failure)?.message ?: retry.message,
                                    )
                                    onError(context.getString(R.string.no_barcodes_found))
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to scan image from gallery")
                        onError(context.getString(R.string.no_barcodes_found))
                    }
                }
            }
        }

    val isCameraGranted = cameraPermissionState.status.isGranted

    val onUploadQr: () -> Unit = {
        if (!isPickerLaunched) {
            isPickerLaunched = true
            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    ScanQrLayout(
        uiModel = uiModel,
        onDismiss = onDismiss,
        onUploadQr = onUploadQr,
        showBottomBar = isCameraGranted,
        toggleMoreInfo = onMoreInfo,
    ) {
        if (isCameraGranted) {
            ScanViewport(isFrameHighlighted = isFrameHighlighted) {
                QrCameraScreen(
                    onSuccess = onSuccess,
                    onError = onError,
                    executor = executor,
                    onAutoFocusTriggered = {
                        isFrameHighlighted = true
                        coroutineScope.launch {
                            delay(300)
                            isFrameHighlighted = false
                        }
                    },
                )
            }
        } else {
            if (!hasRequestedPermission && !cameraPermissionState.status.shouldShowRationale) {
                LaunchedEffect(Unit) {
                    hasRequestedPermission = true
                    cameraPermissionState.launchPermissionRequest()
                }
            } else {
                ErrorView(
                    title = stringResource(R.string.camera_permission_denied),
                    buttonUiModel =
                        ErrorViewButtonUiModel(
                            text = stringResource(R.string.camera_permission_open_settings),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            },
                        ),
                    secondaryButtonUiModel =
                        ErrorViewButtonUiModel(
                            text = stringResource(R.string.scan_qr_upload_qr_code),
                            onClick = onUploadQr,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ScanQrLayout(
    uiModel: ScanQrUiModel,
    onDismiss: () -> Unit,
    onUploadQr: () -> Unit,
    showBottomBar: Boolean,
    toggleMoreInfo: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var infoButtonBottomEnd by remember { mutableStateOf(Offset.Zero) }
    var containerPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val screenWidthDp = with(density) { containerWidth.toDp() }
    val hintBoxWidth = minOf(336.dp, screenWidthDp - 32.dp)
    val space = 2.dp

    Box(
        modifier =
            Modifier.onGloballyPositioned {
                containerPosition = Offset(x = it.positionInWindow().x, y = it.positionInWindow().y)
            }
    ) {
        Scaffold(
            bottomBar = { if (showBottomBar) ScanQrBottomBar(onUploadQr = onUploadQr) },
            topBar = {
                ScanQrTopBar(
                    onBackClick = onDismiss,
                    onMoreInfo = toggleMoreInfo,
                    onInfoButtonPositioned = { infoButtonBottomEnd = it },
                )
            },
        ) { contentPadding ->
            Box(
                modifier =
                    Modifier.background(Theme.v2.colors.text.periwinkle)
                        .background(brush = Theme.v2.colors.gradients.scanQrBackground)
                        .padding(contentPadding)
            ) {
                content()
                AnimatedVisibility(
                    modifier = Modifier.padding(horizontal = V3Scaffold.PADDING_HORIZONTAL),
                    visible = uiModel.error != null,
                ) {
                    Banner(
                        text = uiModel.error.orEmpty(),
                        variant = BannerVariant.Warning,
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    )
                }
            }
        }

        val hintBoxWidthPx = hintBoxWidth.roundToPx()
        val spacePx = space.roundToPx()
        HintBox(
            modifier = Modifier.width(hintBoxWidth),
            isVisible = uiModel.isTipVisible,
            isPointerTriangleOnTop = true,
            offset =
                IntOffset(
                    x = (infoButtonBottomEnd.x - containerPosition.x).toInt() - hintBoxWidthPx,
                    y = (infoButtonBottomEnd.y - containerPosition.y).toInt() + spacePx,
                ),
            pointerAlignment = Alignment.End,
            onDismissClick = toggleMoreInfo,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.scan_qr_hint_title),
                    style =
                        Theme.brockmann.supplementary.footnote.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                        ),
                    color = Theme.v2.colors.text.inverse,
                )
                UiSpacer(size = 4.dp)
                Text(
                    text = stringResource(R.string.scan_qr_hint_message),
                    style = Theme.brockmann.supplementary.footnote.copy(letterSpacing = 0.2.sp),
                    color = Theme.v2.colors.text.inverse,
                )
            }
        }
    }
}

@Composable
private fun ScanQrTopBar(
    onBackClick: () -> Unit,
    onMoreInfo: () -> Unit,
    onInfoButtonPositioned: (Offset) -> Unit = {},
) {
    V3Topbar(
        title = stringResource(R.string.home_screen_scan_qr_code),
        onBackClick = onBackClick,
        actions = {
            VsCircleButton(
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInWindow()
                        val size = coordinates.size
                        onInfoButtonPositioned(
                            Offset(x = pos.x + size.width, y = pos.y + size.height)
                        )
                    },
                onClick = onMoreInfo,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                icon = R.drawable.circleinfo,
            )
        },
        transparentBackground = true,
    )
}

@Composable
private fun ScanQrBottomBar(onUploadQr: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VsButton(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
            onClick = onUploadQr,
            label = stringResource(R.string.scan_qr_upload_qr_code),
            variant = VsButtonVariant.CTA,
        )
    }
}

@Composable
private fun ScanViewport(
    isFrameHighlighted: Boolean,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val cornerRadius = 24.dp
    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = V3Scaffold.PADDING_HORIZONTAL)
                .padding(top = V3Scaffold.PADDING_VERTICAL, bottom = 40.dp)
                .drawBehind {
                    val shadowColor = Color.Black.copy(alpha = 0.25f)
                    val blurRadius = 4.dp.toPx()
                    val offsetY = 4.dp.toPx()
                    val cornerRadius = cornerRadius.toPx()
                    drawIntoCanvas { canvas ->
                        val paint =
                            Paint().also { p ->
                                p.asFrameworkPaint().apply {
                                    this.color = shadowColor.toArgb()
                                    this.maskFilter = BlurMaskFilter(blurRadius, Blur.NORMAL)
                                }
                            }
                        canvas.drawRoundRect(
                            left = 0f,
                            top = offsetY,
                            right = size.width,
                            bottom = size.height + offsetY,
                            radiusX = cornerRadius,
                            radiusY = cornerRadius,
                            paint = paint,
                        )
                    }
                }
                .clip(RoundedCornerShape(size = cornerRadius))
                .border(
                    width = 1.dp,
                    brush = Theme.v2.colors.gradients.scanQrViewportBorder,
                    shape = RoundedCornerShape(size = cornerRadius),
                )
    ) {
        content()
    }
}

@Composable
private fun QrCameraScreen(
    onSuccess: (List<Barcode>) -> Unit,
    onError: (String) -> Unit,
    executor: Executor,
    onAutoFocusTriggered: () -> Unit,
) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(localContext) }

    // Key to force AndroidView recreation when returning from background
    var viewKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewKey++
                    cameraProviderFuture.addListener(
                        {
                            try {
                                cameraProviderFuture.get().unbindAll()
                            } catch (_: Exception) {
                                Timber.e("Failed to unbind camera provider")
                            }
                        },
                        ContextCompat.getMainExecutor(localContext),
                    )
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            localContext.unbindCameraListener(cameraProviderFuture)
        }
    }

    key(viewKey) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                context.setupCamera(
                    lifecycleOwner,
                    executor,
                    cameraProviderFuture,
                    onSuccess,
                    onError,
                    onAutoFocusTriggered,
                )
            },
        )
    }
}

fun createScanner() =
    BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

private sealed interface ScanResult {
    data class Success(val barcodes: List<Barcode>) : ScanResult

    data class Failure(val message: String) : ScanResult
}

private suspend fun scanImage(inputImage: InputImage): ScanResult =
    suspendCancellableCoroutine { continuation ->
        val scanner = createScanner()
        try {
            scanner
                .process(inputImage)
                .addOnSuccessListener { barcodes ->
                    continuation.resume(ScanResult.Success(barcodes))
                }
                .addOnFailureListener { error ->
                    Timber.e(error, "Failed to scan image for barcodes")
                    val errorMessage =
                        when (error) {
                            is IllegalArgumentException -> "Unsupported image format"
                            is IllegalStateException -> "ML Kit scanner not initialized"
                            else -> error.message ?: error.toString()
                        }
                    continuation.resume(ScanResult.Failure(errorMessage))
                }
                .addOnCompleteListener { scanner.close() }
        } catch (e: CancellationException) {
            scanner.close()
            throw e
        } catch (e: Exception) {
            scanner.close()
            Timber.e(e, "Barcode scanner unavailable")
            continuation.resume(
                ScanResult.Failure("Barcode scanner unavailable: ${e.message ?: e.toString()}")
            )
        }
    }

@Preview
@Composable
private fun ScanQrScreenPreview() {
    ScanQrLayout(
        uiModel = ScanQrUiModel(isTipVisible = false),
        onDismiss = {},
        onUploadQr = {},
        showBottomBar = true,
        toggleMoreInfo = {},
    ) {
        ScanViewport(isFrameHighlighted = false)
    }
}
