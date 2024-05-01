package com.voltix.wallet.presenter.keygen

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import tss.Tss
enum class KeygenState {
    CreatingInstance,
    KeygenECDSA,
    KeygenEdDSA,
    ReshareECDSA,
    ReshareEdDSA,
    Success,
    ERROR
}

class GeneratingKeyViewModel(
    private val vault: Vault,
    private val action: TssAction,
    private val keygenCommittee: List<String>,
    private val oldCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
) {
    val currentState: MutableState<KeygenState> = mutableStateOf(KeygenState.CreatingInstance)
    val errorMessage: MutableState<String> = mutableStateOf("")

}