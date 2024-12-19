package com.vultisig.wallet.ui.models.sign

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.CustomMessagePayloadDto
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import vultisig.keysign.v1.CustomMessagePayload
import java.util.UUID
import javax.inject.Inject

internal data class SignMessageFormUiModel(
    val isLoading: Boolean = false,
)

@HiltViewModel
internal class SignMessageFormViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val customMessagePayloadRepo: CustomMessagePayloadRepo,
) : ViewModel() {

    val state = MutableStateFlow(SignMessageFormUiModel())

    val methodFieldState = TextFieldState()
    val messageFieldState = TextFieldState()

    private var vaultId: VaultId? = null

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
    }

    fun sign() {
        val vaultId = vaultId ?: return

        viewModelScope.launch {
            val payload = CustomMessagePayloadDto(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                payload = CustomMessagePayload(
                    method = methodFieldState.text.toString(),
                    message = messageFieldState.text.toString(),
                )
            )
            customMessagePayloadRepo.add(payload)
            sendNavigator.navigate(
                SendDst.VerifyTransaction(
                    transactionId = payload.id,
                    vaultId = vaultId,
                )
            )
        }
    }

}