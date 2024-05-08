package com.vultisig.wallet.presenter.keysign

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.models.Vault

class KeysignShareViewModel : ViewModel() {
    var vault: Vault? = null
    var keysignPayload: KeysignPayload? = null
}