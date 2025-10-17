package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetFlowTypeUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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

}