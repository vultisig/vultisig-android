package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class TokenSelectionUiModel(
    val tokens: List<TokenUiModel> = emptyList(),
)

@Immutable
internal data class TokenUiModel(
    val isEnabled: Boolean,
    val coin: Coin,
)

@HiltViewModel
internal class TokenSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val oneInchApi: OneInchApi,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val chainId: String =
        requireNotNull(savedStateHandle[ARG_CHAIN_ID])

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    init {
        loadChains()

        viewModelScope.launch {
            val resp = oneInchApi.getTokens(Chain.ethereum)
            println(resp)
        }
    }

    fun enableToken(coin: Coin) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: error("No vault with $vaultId")

            val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                coin,
                vault
            )
            val updatedCoin = coin.copy(
                address = address,
                hexPublicKey = derivedPublicKey
            )

            vaultRepository.addTokenToVault(vaultId, updatedCoin)

            loadChains()
        }
    }

    fun disableToken(coin: Coin) {
        viewModelScope.launch {
            vaultRepository.deleteTokenFromVault(vaultId, coin.id)
            loadChains()
        }
    }

    private fun loadChains() {
        val chain = Chain.fromRaw(chainId)
        viewModelScope.launch {
            tokenRepository.getChainTokens(chain)
                .catch { e ->
                    // todo handle error
                    Timber.e(e)
                }
                .map { tokens -> tokens.filter { !it.isNativeToken } }
                .zip(
                    vaultRepository.getEnabledTokens(vaultId)
                        .map { enabled -> enabled.map { it.id }.toSet() }
                ) { tokens, enabledTokens ->
                    tokens
                        .asSequence()
                        .map { token ->
                            TokenUiModel(
                                isEnabled = token.id in enabledTokens,
                                coin = token,
                            )
                        }
                        .sortedWith(
                            compareBy(
                                { !it.isEnabled },
                                { it.coin.ticker },
                            )
                        )
                        .toList()
                }.collect { tokens ->
                    uiState.update { it.copy(tokens = tokens) }
                }
        }
    }

}