package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_AMOUNT
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_DST_ADDRESS
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class VerifyTransactionUiModel(
    val srcAddress: String = "",
    val dstAddress: String = "",
    val tokenValue: String = "",
    val fiatValue: String = "",
    val fiatCurrency: String = "",
    val gasValue: String = "",
)

@HiltViewModel
internal class VerifyTransactionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultDB: VaultDB,
    private val addressRepository: ChainAccountAddressRepository,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private val chainId: String = requireNotNull(savedStateHandle[ARG_CHAIN_ID])
    private val tokenId: String = requireNotNull(savedStateHandle[ARG_TOKEN_ID])
    private val dstAddress: String = requireNotNull(savedStateHandle[ARG_DST_ADDRESS])
    private val amount: String = requireNotNull(savedStateHandle[ARG_AMOUNT])

    private val chain: Chain = chainId
        .let(Chain::fromRaw)

    val uiState = MutableStateFlow(
        VerifyTransactionUiModel(
            dstAddress = dstAddress,
            tokenValue = amount,
        )
    )

    init {
        viewModelScope.launch {
            val vault = requireNotNull(vaultDB.select(vaultId))

            val address = addressRepository.getAddress(chain, vault)

            uiState.update { it.copy(srcAddress = address) }

            val token = tokenRepository.getToken(tokenId)
                .first()

            val tokenValue = TokenValue(amount.toBigInteger(), token.decimal)

            uiState.update { it.copy(tokenValue = tokenValue.decimal.toPlainString()) }
        }
    }

    fun joinKeysign() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Keysign(
                    vaultId = vaultId,
                    chainId = chainId,
                    tokenId = tokenId,
                    dstAddress = dstAddress,
                    amount = amount,
                )
            )
        }
    }

}