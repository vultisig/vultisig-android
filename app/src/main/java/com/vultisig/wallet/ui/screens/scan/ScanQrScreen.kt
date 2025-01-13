@file:OptIn(ExperimentalPermissionsApi::class)

package com.vultisig.wallet.ui.screens.scan

import android.content.Context
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ScanQrViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.addWhiteBorder
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import com.vultisig.wallet.ui.utils.uriToBitmap
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal const val ARG_QR_CODE = "qr_code"

@Composable
internal fun ScanQrAndJoin(
    navController: NavController,
    viewModel: ScanQrViewModel = hiltViewModel(),
) {
    ScanQrScreen(
        onDismiss = { navController.popBackStack() },
        onScanSuccess = viewModel::joinOrSend,
    )
}

@Composable
internal fun ScanQrScreen(
    navController: NavController,
    viewModel: ScanQrViewModel = hiltViewModel(),
) {
    ScanQrScreen(
        onDismiss = { navController.popBackStack() },
        onScanSuccess = { qr ->
            if (viewModel.getFlowType(qr) == JOIN_SEND_ON_ADDRESS_FLOW) {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(ARG_QR_CODE, qr.getAddressFromQrCode())
            }
            navController.popBackStack()
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ScanQrScreen(
    onDismiss: () -> Unit,
    roundedCorners: Boolean = false,
    onScanSuccess: (qr: String) -> Unit,
) {
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



    val pickMedia = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        coroutineScope.launch {
            if (uri != null) {
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
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (cameraPermissionState.status.isGranted.not())
                MultiColorButton(
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    text = stringResource(id = R.string.scan_qr_screen_return_vault),
                    onClick = onDismiss,
                )

        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(if (roundedCorners) PaddingValues(0.dp) else paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                QrCameraScreen(
                    roundedCorners = roundedCorners,
                    onSuccess = onSuccess,
                )
                Image(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(40.dp),
                    painter = painterResource(id = R.drawable.camera_frame),
                    contentDescription = null,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    MultiColorButton(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = 12.dp,
                                vertical = 16.dp,
                            ),
                        iconSize = 0.dp,
                        onClick = {
                            pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        content = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UiIcon(
                                    modifier = Modifier
                                        .padding(start = 16.dp),
                                    size = 35.dp,
                                    drawableResId = R.drawable.ic_gallery_min,
                                    tint = Theme.colors.oxfordBlue600Main,
                                )
                                Text(
                                    modifier = Modifier
                                        .weight(1f),
                                    textAlign = TextAlign.Center,
                                    text = stringResource(id = R.string.scan_qr_upload_from_gallery),
                                    style = Theme.montserrat.subtitle2,
                                    color = Theme.colors.oxfordBlue600Main,
                                )
                                UiSpacer(size = 41.dp)
                            }
                        }
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

@Composable
private fun QrCameraScreen(
    roundedCorners: Boolean = false,
    onSuccess: (List<Barcode>) -> Unit,

    ) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(localContext)
    }

    DisposableEffect(Unit) {
        onDispose {
            unbindCameraListener(cameraProviderFuture, localContext)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().let {
            if (roundedCorners) it.clip(RoundedCornerShape(15.dp,15.dp, 0.dp, 0.dp)) else it
        },
        factory = { context ->
            val previewView = PreviewView(context)
            val resolutionStrategy = ResolutionStrategy(
                Size(1200, 1200),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
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

            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(context),
                BarcodeAnalyzer {
                    unbindCameraListener(cameraProviderFuture, localContext)
                    onSuccess(it)
                }
            )

            try {
                cameraProviderFuture.get().bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Throwable) {
                Timber.e(context.getString(R.string.camera_bind_error, e.localizedMessage), e)
            }

            previewView
        }
    )
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