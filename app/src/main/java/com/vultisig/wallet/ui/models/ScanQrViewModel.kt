package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetFlowTypeUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ScanQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val getFlowTypeUseCase: GetFlowTypeUseCase,
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase,
) : ViewModel() {

    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]

    fun joinOrSend(qr: String) = viewModelScope.launch {
        val dst = getDirectionByQrCodeUseCase(qr, vaultId)
        // TODO totally a hack
        if (dst is Destination.JoinKeygen) {
            navigator.route(
                Route.Keygen.Join(
                    qr = dst.qr,
                )
            )
        } else {
            navigator.navigate(dst)
        }
    }

    fun getFlowType(qr: String): String {
        return getFlowTypeUseCase(qr)
    }
}