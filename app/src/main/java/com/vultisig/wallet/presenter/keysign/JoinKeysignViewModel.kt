package com.vultisig.wallet.presenter.keysign

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keygen.JoinKeygenState

enum class JoinKeysigState {
    DiscoveryingSessionID,
    DiscoverService,
    JoinKeysign,
    WaitingForKeysignStart,
    Keysign,
    FailedToStart,
    Error
}

class JoinKeysignViewModel : ViewModel() {
    private var _currentVault: Vault = Vault("temp vault")
    var currentState: MutableState<JoinKeygenState> =
        mutableStateOf(JoinKeygenState.DiscoveryingSessionID)
    var errorMessage: MutableState<String> = mutableStateOf("")
    private var _localPartyID: String = ""
    private var _sessionID: String = ""
    private var _useVultisigRelay: Boolean = false

    fun setData(vault: Vault){
        _currentVault = vault
        _localPartyID = vault.localPartyID
    }
    fun setScanResult(content:String){
        val qrCodeContent = DeepLinkHelper(content).getJsonData()
        KeysignMesssage
    }
}