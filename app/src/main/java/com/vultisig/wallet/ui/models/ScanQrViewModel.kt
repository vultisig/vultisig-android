package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
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
            val flowType = try {
                DeepLinkHelper(qr).getFlowType()
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse QR-code via DeepLinkHelper")
                JOIN_SEND_ON_ADDRESS_FLOW
            }
            val qrBase64 = Base64.UrlSafe.encode(qr.toByteArray())
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
                        )
                    }
                    JOIN_SEND_ON_ADDRESS_FLOW -> {
                        Destination.Send(vaultId = requireNotNull(vaultId), address = qr)
                    }

                    else -> throw IllegalArgumentException(
                        "Unsupported QR-code flowType: $flowType"
                    )
                }
            )
        }
    }

    companion object {
        private const val JOIN_KEYSIGN_FLOW = "SignTransaction"
        private const val JOIN_KEYGEN_FLOW = "NewVault"
        private const val JOIN_SEND_ON_ADDRESS_FLOW = "SendOnAddress"
    }

}