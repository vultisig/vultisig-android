package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import com.vultisig.wallet.ui.utils.isJson
import com.vultisig.wallet.ui.utils.isReshare
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@HiltViewModel
internal class ScanQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]

    @OptIn(ExperimentalEncodingApi::class)
    fun joinOrSend(qr: String) {
        Timber.d("joinOrSend(qr = $qr)")
        viewModelScope.launch {
            val flowType = getFlowType(qr)
            val qrBase64 = Base64.UrlSafe.encode(qr.toByteArray())
            try {
                navigator.navigate(
                    when (flowType) {
                        JOIN_KEYSIGN_FLOW -> {
                            Destination.JoinKeysign(
                                vaultId = requireNotNull(vaultId),
                                qr = qrBase64,
                            )
                        }

                        JOIN_KEYGEN_FLOW -> {
                            Destination.JoinKeygen(
                                qr = qrBase64,
                                isReshare = qr.isReshare(),
                            )
                        }

                        JOIN_SEND_ON_ADDRESS_FLOW -> {
                            val address = qr.getAddressFromQrCode()
                            Destination.Send(vaultId = requireNotNull(vaultId), address = address)
                        }

                        else -> Destination.ScanError
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to destination")
            }
        }
    }

    fun getFlowType(qr: String): String {
        return try {
            DeepLinkHelper(qr).getFlowType()?: throw IllegalArgumentException("No flowType found")
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse QR-code via DeepLinkHelper")
            if (qr.isJson()) {
                UNKNOWN_FLOW
            } else {
                JOIN_SEND_ON_ADDRESS_FLOW
            }
        }
    }

    companion object {
        const val JOIN_KEYSIGN_FLOW = "SignTransaction"
        const val JOIN_KEYGEN_FLOW = "NewVault"
        const val JOIN_SEND_ON_ADDRESS_FLOW = "SendOnAddress"
        const val UNKNOWN_FLOW = "Unknown"
    }

}