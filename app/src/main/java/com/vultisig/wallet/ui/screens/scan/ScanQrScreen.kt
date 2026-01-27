@file:OptIn(ExperimentalPermissionsApi::class)

package com.vultisig.wallet.ui.screens.scan

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.banners.BannerVariant
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.bottomsheet.VsBottomSheet
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.ScanQrUiModel
import com.vultisig.wallet.ui.models.ScanQrViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.addWhiteBorder
import com.vultisig.wallet.ui.utils.setupCamera
import com.vultisig.wallet.ui.utils.unbindCameraListener
import com.vultisig.wallet.ui.utils.uriToBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanQrScreen(
    viewModel: ScanQrViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    VsBottomSheet(onDismissRequest = viewModel::back) {
        ScanQrScreen(
            uiModel = uiModel,
            onDismiss = viewModel::back,
            onScanSuccess = viewModel::process,
            onError = viewModel::handleError
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ScanQrScreen(
    onDismiss: () -> Unit,
    onScanSuccess: (qr: String) -> Unit,
    uiModel: ScanQrUiModel,
    onError: (String) -> Unit = {},
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
                Timber.d(
                    context.getString(
                        R.string.successfully_scanned_barcode,
                        barcodeValue
                    )
                )
                if (barcodeValue != null) {
                    onScanSuccess(barcodeValue)
                }
            }
        } else {
            onError(context.getString(R.string.no_barcodes_found))
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdownNow()
        }
    }

    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        coroutineScope.launch {
            if (uri != null) {
                try {
                    val result = scanImage(
                        InputImage.fromFilePath(
                            context,
                            uri
                        ),
                        onError
                    )
                    val barcodes = result.ifEmpty {
                        val bitmap = requireNotNull(
                            uriToBitmap(
                                context.contentResolver,
                                uri
                            )
                        )
                            .addWhiteBorder(2F)
                        val inputImage = InputImage.fromBitmap(
                            bitmap,
                            0
                        )
                        val resultBarcodes = scanImage(
                            inputImage,
                            onError
                        )
                        bitmap.recycle()
                        resultBarcodes
                    }
                    onSuccess(barcodes)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    VsScaffold(
        onBackClick = onDismiss,
        title = stringResource(R.string.scan_qr_screen_title)
    ) {
        Box {
            if (cameraPermissionState.status.isGranted) {
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
                    }
                )


                Image(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(40.dp),
                    painter = if (isFrameHighlighted) {
                        painterResource(id = R.drawable.vs_camera_frame_highlight)
                    } else {
                        painterResource(id = R.drawable.vs_camera_frame)
                    },
                    contentDescription = null,
                )

                AnimatedVisibility(uiModel.error != null) {
                    Banner(
                        text = uiModel.error.orEmpty(),
                        variant = BannerVariant.Warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                    )
                }
            } else if (cameraPermissionState.status.shouldShowRationale ||
                cameraPermissionState.status.isGranted.not()
            ) {
                SideEffect {
                    cameraPermissionState.launchPermissionRequest()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        Image(
                            painter = painterResource(id = R.drawable.danger),
                            contentDescription = null,
                            Modifier.width(65.dp)
                        )

                        UiSpacer(size = 16.dp)
                        Text(
                            text = stringResource(R.string.camera_permission_denied),
                            textAlign = TextAlign.Center,
                            color = Theme.colors.neutrals.n100,
                            style = Theme.montserrat.subtitle1,
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        vertical = 16.dp
                    )
            ) {
                if (cameraPermissionState.status.isGranted.not()) {
                    VsButton(
                        label = stringResource(id = R.string.scan_qr_screen_return_vault),
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()

                    )
                    UiSpacer(
                        size = 12.dp
                    )
                }

                VsButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    label = stringResource(id = R.string.scan_qr_upload_from_gallery),
                    iconLeft = R.drawable.ic_qr_upload
                )
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun QrCameraScreen(
    onSuccess: (List<Barcode>) -> Unit,
    onError: (String) -> Unit,
    executor: Executor,
    onAutoFocusTriggered: () -> Unit,
) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(localContext)
    }

    // Key to force AndroidView recreation when returning from background
    var viewKey by remember {
        mutableIntStateOf(0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Force AndroidView to recreate by changing the key
                    viewKey++
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (e: Exception) {
                        // Provider might not be ready yet
                        Timber.e(e)
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            localContext.unbindCameraListener(
                cameraProviderFuture,
            )
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
            }
        )
    }
}


fun createScanner() = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
)

private suspend fun scanImage(inputImage: InputImage, onError: (String) -> Unit) =
    suspendCoroutine { continuation ->
        try {
            createScanner()
                .process(inputImage)
                .addOnSuccessListener { barcodes -> continuation.resume(barcodes) }
                .addOnFailureListener { error ->
                    Timber.e(
                        error,
                        "Failed to scan image for barcodes"
                    )
                    val errorMessage = when (error) {
                        is IllegalArgumentException -> "Unsupported image format"
                        is IllegalStateException -> "ML Kit scanner not initialized"
                        else -> error.message ?: error.toString()
                    }
                    onError(errorMessage)
                    continuation.resume(emptyList())
                }

        } catch (e: Exception) {
            onError("Barcode scanner unavailable: ${e.message}")
            continuation.resume(emptyList())
        }
    }
