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
import com.vultisig.wallet.ui.utils.SnackbarFlow
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
    private val snackbarFlow: SnackbarFlow,
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
                    val dst = getDirectionByQrCodeUseCase(
                        qr,
                        args.vaultId
                    )
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

    suspend fun handleError(error: String) {
        snackbarFlow.showMessage(error)
    }

    private fun createScanner() = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )


}