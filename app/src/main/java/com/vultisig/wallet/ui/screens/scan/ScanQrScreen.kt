@file:OptIn(ExperimentalPermissionsApi::class)

package com.vultisig.wallet.ui.screens.scan

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.ScanQrViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.addWhiteBorder
import com.vultisig.wallet.ui.utils.uriToBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
internal fun ScanQrScreen(
    viewModel: ScanQrViewModel = hiltViewModel(),
) {
    ScanQrScreen(
        onDismiss = viewModel::back,
        onScanSuccess = viewModel::process,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ScanQrScreen(
    onDismiss: () -> Unit,
    onScanSuccess: (qr: String) -> Unit,
) {
    var isFrameHighlighted by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanned by remember { mutableStateOf(false) }

    val onSuccess: (List<Barcode>) -> Unit = { barcodes ->
        if (barcodes.isNotEmpty() && !isScanned) {
            isScanned = true
            val barcode = barcodes.first()
            val barcodeValue = barcode.rawValue
            Timber.d(context.getString(R.string.successfully_scanned_barcode, barcodeValue))
            if (barcodeValue != null) {
                onScanSuccess(barcodeValue)
            }
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
                    val result = scanImage(InputImage.fromFilePath(context, uri))
                    val barcodes = if (result.isEmpty()) {
                        val bitmap = requireNotNull(uriToBitmap(context.contentResolver, uri))
                            .addWhiteBorder(2F)
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val resultBarcodes = scanImage(inputImage)
                        bitmap.recycle()
                        resultBarcodes
                    } else result
                    onSuccess(barcodes)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (cameraPermissionState.status.isGranted.not())
                VsButton(
                    label = stringResource(id = R.string.scan_qr_screen_return_vault),
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                )

        },
        topBar = {
            VsTopAppBar(
                onBackClick = onDismiss,
                title = stringResource(R.string.scan_qr_screen_title)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                QrCameraScreen(
                    onSuccess = onSuccess,
                    executor = executor,
                    onAutoFocusTriggered =  {
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

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    VsButton(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = 12.dp,
                                vertical = 16.dp,
                            ),
                        onClick = {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        label = stringResource(id = R.string.scan_qr_upload_from_gallery),
                        iconLeft = R.drawable.ic_qr_upload
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
                            color = Theme.colors.neutral100,
                            style = Theme.montserrat.subtitle1,
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun QrCameraScreen(
    onSuccess: (List<Barcode>) -> Unit,
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
                Lifecycle.Event.ON_PAUSE -> {
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
            unbindCameraListener(cameraProviderFuture, localContext)
        }
    }

    key(viewKey) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val resolutionStrategy = ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(resolutionStrategy)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                preview.surfaceProvider = previewView.surfaceProvider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(
                    executor,
                    BarcodeAnalyzer {
                        unbindCameraListener(cameraProviderFuture, localContext)
                        onSuccess(it)
                    }
                )

                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()

                    Thread.sleep(400)

                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis,
                    )

                    // In some devices auto-focus does not work very well
                    // We should allow user to touch and perform focus,
                    // the autofocus initiated by a tap will "stick" at that point until
                    // another tap occurs
                    previewView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            Timber.d("Auto-focus requested : ${event.x} ${event.y}")
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point)
                                .disableAutoCancel()
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                            onAutoFocusTriggered()
                        }
                        true
                    }

                } catch (e: Throwable) {
                    Timber.e(e)
                }

                previewView
            }
        )
    }
}

private fun unbindCameraListener(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    context: Context,
) {
    cameraProviderFuture.addListener(
        {
            cameraProviderFuture.get().unbindAll()
        },
        ContextCompat.getMainExecutor(context)
    )
}

private class BarcodeAnalyzer(
    private val onSuccess: (List<Barcode>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = createScanner()

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { image ->
            scanner.process(
                InputImage.fromMediaImage(
                    image, imageProxy.imageInfo.rotationDegrees
                )
            ).addOnSuccessListener { barcode ->
                barcode?.takeIf { it.isNotEmpty() }
                    ?.let(onSuccess)
            }.addOnCompleteListener {
                imageProxy.close()
            }
        }
    }
}

private fun createScanner() = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
)

private suspend fun scanImage(inputImage: InputImage) = suspendCoroutine { cont ->
    createScanner()
        .process(inputImage)
        .addOnSuccessListener { cont.resume(it) }
}