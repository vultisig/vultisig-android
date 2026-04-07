@file:OptIn(ExperimentalPermissionsApi::class)

package com.vultisig.wallet.ui.screens.scan

import android.Manifest
import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.vultisig.wallet.R.drawable.vs_camera_frame
import com.vultisig.wallet.R.drawable.vs_camera_frame_highlight
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.banners.BannerVariant
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.errors.ErrorView
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
        scrimColor = Color(0xFF02122B),
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
            coroutineScope.launch {
                if (uri != null) {
                    try {
                        val result = scanImage(InputImage.fromFilePath(context, uri), onError)
                        val barcodes =
                            result.ifEmpty {
                                val bitmap =
                                    requireNotNull(uriToBitmap(context.contentResolver, uri))
                                        .addWhiteBorder(2F)
                                val inputImage = InputImage.fromBitmap(bitmap, 0)
                                val resultBarcodes = scanImage(inputImage, onError)
                                bitmap.recycle()
                                resultBarcodes
                            }
                        onSuccess(barcodes)
                    } catch (_: Exception) {
                        Timber.e("Failed to scan image from gallery")
                    }
                }
            }
        }

    val isCameraGranted = cameraPermissionState.status.isGranted

    ScanQrLayout(
        uiModel = uiModel,
        showReturnButton = isCameraGranted.not(),
        onDismiss = onDismiss,
        onUploadQr = { pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
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
        } else if (
            cameraPermissionState.status.shouldShowRationale ||
                cameraPermissionState.status.isGranted.not()
        ) {
            SideEffect(cameraPermissionState::launchPermissionRequest)
            ErrorView(
                title = stringResource(R.string.camera_permission_denied),
                buttonUiModel = null,
            )
        }
    }
}

@Composable
private fun ScanQrLayout(
    uiModel: ScanQrUiModel,
    showReturnButton: Boolean,
    onDismiss: () -> Unit,
    onUploadQr: () -> Unit,
    toggleMoreInfo: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var infoButtonBottomCenter by remember { mutableStateOf(Offset.Zero) }
    var containerPosition by remember { mutableStateOf(Offset.Zero) }
    val hintBoxWidth = 336.dp
    val space = 2.dp

    Box(
        modifier =
            Modifier.onGloballyPositioned {
                containerPosition = Offset(x = it.positionInWindow().x, y = it.positionInWindow().y)
            }
    ) {
        Scaffold(
            bottomBar = {
                ScanQrBottomBar(
                    showReturnButton = showReturnButton,
                    onDismiss = onDismiss,
                    onUploadQr = onUploadQr,
                )
            },
            topBar = {
                ScanQrTopBar(
                    onBackClick = onDismiss,
                    onMoreInfo = toggleMoreInfo,
                    onInfoButtonPositioned = { infoButtonBottomCenter = it },
                )
            },
        ) { contentPadding ->
            Box(
                modifier =
                    Modifier.background(Color(0xFF95AEE0))
                        .background(brush = GradientBackground)
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
                    x = (infoButtonBottomCenter.x - containerPosition.x).toInt() - hintBoxWidthPx,
                    y = (infoButtonBottomCenter.y - containerPosition.y).toInt() + spacePx,
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
private fun ScanQrBottomBar(
    showReturnButton: Boolean,
    onDismiss: () -> Unit,
    onUploadQr: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showReturnButton) {
            VsButton(
                label = stringResource(id = R.string.scan_qr_screen_return_vault),
                onClick = onDismiss,
                variant = VsButtonVariant.CTA,
            )
            UiSpacer(size = 12.dp)
        }

        VsButton(
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
                    brush = ViewportBorderGradient,
                    shape = RoundedCornerShape(size = cornerRadius),
                )
    ) {
        content()

        CenterFrame(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(40.dp),
            isFrameHighlighted = isFrameHighlighted,
        )
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
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (_: Exception) {
                        Timber.e("Failed to unbind camera provider")
                    }
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

@Composable
private fun CenterFrame(isFrameHighlighted: Boolean, modifier: Modifier = Modifier) {
    Image(
        modifier = modifier,
        painter =
            if (isFrameHighlighted) {
                painterResource(id = vs_camera_frame_highlight)
            } else {
                painterResource(id = vs_camera_frame)
            },
        contentDescription = null,
    )
}

fun createScanner() =
    BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

private suspend fun scanImage(inputImage: InputImage, onError: (String) -> Unit) =
    suspendCancellableCoroutine { continuation ->
        try {
            createScanner()
                .process(inputImage)
                .addOnSuccessListener { barcodes -> continuation.resume(barcodes) }
                .addOnFailureListener { error ->
                    Timber.e(error, "Failed to scan image for barcodes")
                    val errorMessage =
                        when (error) {
                            is IllegalArgumentException -> "Unsupported image format"
                            is IllegalStateException -> "ML Kit scanner not initialized"
                            else -> error.message ?: error.toString()
                        }
                    onError(errorMessage)
                    continuation.resume(emptyList())
                }
        } catch (_: Exception) {
            onError("Barcode scanner unavailable")
            continuation.resume(emptyList())
        }
    }

@Preview
@Composable
private fun ScanQrScreenPreview() {
    ScanQrLayout(
        uiModel = ScanQrUiModel(),
        showReturnButton = false,
        onDismiss = {},
        onUploadQr = {},
        toggleMoreInfo = {},
    ) {
        ScanViewport(isFrameHighlighted = false)
    }
}

private val GradientBackground =
    Brush.verticalGradient(
        colorStops =
            arrayOf(
                0.0000f to Color(0xFF02122B),
                0.0043f to Color(0xFD02122B),
                0.0178f to Color(0xF602122B),
                0.0409f to Color(0xEB02122B),
                0.0737f to Color(0xDA02122B),
                0.1159f to Color(0xC402122B),
                0.1660f to Color(0xAB02122B),
                0.2214f to Color(0x8E02122B),
                0.2786f to Color(0x7102122B),
                0.3340f to Color(0x5502122B),
                0.3841f to Color(0x3B02122B),
                0.4263f to Color(0x2502122B),
                0.4591f to Color(0x1502122B),
                0.4822f to Color(0x0902122B),
                0.4957f to Color(0x0202122B),
                0.5000f to Color(0x0002122B),
                1.0000f to Color(0x0002122B),
            )
    )

private val ViewportBorderGradient =
    Brush.verticalGradient(colors = listOf(Color(0xFF4879FD), Color(0xFF2B4897)))
