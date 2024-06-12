package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@HiltViewModel
internal class ScanQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])

    @OptIn(ExperimentalEncodingApi::class)
    fun join(qr: String) {
        val qrBase64 = Base64.UrlSafe.encode(qr.toByteArray())
        viewModelScope.launch {
            navigator.navigate(
                Destination.JoinKeysign(
                    vaultId = vaultId,
                    qr = qrBase64,
                )
            )
        }
    }

}