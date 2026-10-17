@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.sign

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.common.isHexPrefixed
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.resolveEcdsaChain
import com.vultisig.wallet.data.repositories.CustomMessagePayloadDto
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import vultisig.keysign.v1.CustomMessagePayload

private const val PERSONAL_SIGN = "personal_sign"

/**
 * Wraps a `personal_sign` message in the EIP-191 envelope (mirrors iOS #5595); co-signers hash the
 * payload as-is. `0x` input is hex data, so its envelope is built from the decoded bytes and
 * re-encoded as hex; plain text stays text.
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun String.forMethod(method: String): String {
    if (!method.equals(PERSONAL_SIGN, ignoreCase = true)) return this
    val prefix = "\u0019Ethereum Signed Message:\n"
    if (isHexPrefixed()) {
        val data = toHexBytes()
        if (data.isNotEmpty()) return "0x" + (prefix.toByteArray() + data.size.toString().toByteArray() + data).toHexString()
    }
    return "$prefix${toByteArray().size}$this"
}

internal data class SignMessageFormUiModel(val isLoading: Boolean = false)

@HiltViewModel
internal class SignMessageFormViewModel
@Inject
constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val customMessagePayloadRepo: CustomMessagePayloadRepo,
    private val vaultRepository: VaultRepository,
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
            val vault = vaultRepository.get(vaultId) ?: return@launch

            val method = methodFieldState.text.toString()
            val message = messageFieldState.text.toString()

            val payload =
                CustomMessagePayloadDto(
                    id = Uuid.random().toString(),
                    vaultId = vaultId,
                    payload =
                        CustomMessagePayload(
                            method = method,
                            message = message.forMethod(method),
                            vaultPublicKeyEcdsa = vault.pubKeyECDSA,
                            vaultLocalPartyId = vault.localPartyID,
                            chain = vault.resolveEcdsaChain(Chain.Ethereum).raw,
                        ),
                )
            customMessagePayloadRepo.add(payload)
            sendNavigator.navigate(
                SendDst.VerifyTransaction(transactionId = payload.id, vaultId = vaultId)
            )
        }
    }
}
