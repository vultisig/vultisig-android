package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Size
import android.view.MotionEvent
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.core.content.ContextCompat
import androidx.navigation.toRoute
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetFlowTypeUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.scan.createScanner
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executor
import javax.inject.Inject

@HiltViewModel
internal class ScanQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val getFlowTypeUseCase: GetFlowTypeUseCase,
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ScanQr>()

    fun process(qr: String) {
        when {
            !args.requestId.isNullOrBlank() -> {
                viewModelScope.launch {
                    requestResultRepository.respond(
                        requestId = args.requestId,
                        result = if (getFlowTypeUseCase(qr) == JOIN_SEND_ON_ADDRESS_FLOW) {
                            qr.getAddressFromQrCode()
                        } else {
                            null
                        }
                    )

                    back()
                }
            }

            else -> {
                viewModelScope.launch {
                    val dst = getDirectionByQrCodeUseCase(qr, args.vaultId)
                    navigator.route(dst)
                }
            }
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    val camera = setupCamera(
        context = localContext,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        executor = executor,
        cameraProviderFuture = cameraProviderFuture,
        onSuccess = onSuccess,
        onAutoFocusTriggered = onAutoFocusTriggered
    )

    fun setupCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: Executor,
        cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
        onSuccess: (List<Barcode>) -> Unit,
        onAutoFocusTriggered: () -> Unit
    ): Camera? {
        val resolutionStrategy = ResolutionStrategy(
            Size(
                1920,
                1080
            ),
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

        return try {
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(
                executor,
                BarcodeAnalyzer {
                    unbindCameraListener(
                        cameraProviderFuture,
                        context
                    )
                    onSuccess(it)
                }
            )

            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

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
                    previewView.performClick()
                }
                true
            }


            camera
        } catch (e: Exception) {
            Timber.e(e)
            null
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


}