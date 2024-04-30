package com.voltix.wallet.presenter.keygen

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.voltix.wallet.common.VoltixRelay
import com.voltix.wallet.models.KeygenDiscoveryPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class KeygenDiscoveryViewModel @Inject constructor(
    private val voltixRelay: VoltixRelay
) : ViewModel() {

    val keyGenPayloadState: State<String>
        get() = _keyGenPayloadState
    private val _keyGenPayloadState: MutableState<String> = mutableStateOf("")

    init {
        generateQRCodePayload()
    }

    private fun generateQRCodePayload() {
        _keyGenPayloadState.value = getJSONPayload()
    }

    private fun getJSONPayload(): String =
        KeygenDiscoveryPayload(
            sessionID = UUID.randomUUID().toString(),
            hexChainCode = getRandomString(32),
            serviceName = getRandomString(16),
            encryptionKeyHex = getRandomBytes(),
            useVoltixRelay = false
        ).toJson()

    private fun getRandomString(length: Int) : String {
        val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }

    private fun getRandomBytes(): String = Random.nextBytes(10).toString()
}