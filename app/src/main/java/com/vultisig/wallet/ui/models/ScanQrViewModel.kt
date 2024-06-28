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

    fun joinOrSend(qr: String) {
        viewModelScope.launch {
            var isJoinSuccessful = false
            try {
                join(qr)
                isJoinSuccessful = true
            } catch (e: Exception) {
                Timber.e(e)
                isJoinSuccessful = false
            }
            if (!isJoinSuccessful) {
                navigator.navigate(
                    Destination.Send(vaultId = requireNotNull(vaultId), address = qr)
                )
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun join(qr: String) {
        val flowType = DeepLinkHelper(qr).getFlowType()
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

                else -> throw IllegalArgumentException(
                    "Unsupported QR-code flowType: $flowType"
                )
            }
        )
    }

    companion object {
        private const val JOIN_KEYSIGN_FLOW = "SignTransaction"
        private const val JOIN_KEYGEN_FLOW = "NewVault"
    }

}