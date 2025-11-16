package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Size
import android.view.MotionEvent
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.scan.createScanner
import com.vultisig.wallet.ui.utils.UiText.StringResource
import timber.log.Timber
import java.util.concurrent.Executor

internal fun Context.closestActivityOrNull(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.closestActivityOrNull()
        else -> null
    }
}

internal fun Context.setupCamera(
    lifecycleOwner: LifecycleOwner,
    executor: Executor,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onSuccess: (List<Barcode>) -> Unit,
    onError: (error: String) -> Unit,
    onAutoFocusTriggered: () -> Unit
): PreviewView {

    val previewView = PreviewView(this)
    try {
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


        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(
            executor,
            BarcodeAnalyzer {
                this.unbindCameraListener(
                    cameraProviderFuture
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
                val point = factory.createPoint(
                    event.x,
                    event.y
                )
                val action = FocusMeteringAction.Builder(point)
                    .disableAutoCancel()
                    .build()
                camera.cameraControl.startFocusAndMetering(action)
                onAutoFocusTriggered()
                previewView.performClick()
            }
            true
        }
    } catch (e: Exception) {
        onError(e.message ?: e.toString())
    }
    return previewView
}

internal fun Context.unbindCameraListener(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
) {
    cameraProviderFuture.addListener(
        {
            cameraProviderFuture.get().unbindAll()
        },
        ContextCompat.getMainExecutor(this)
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
                    image,
                    imageProxy.imageInfo.rotationDegrees
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