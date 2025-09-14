package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ChainSelectionUiModel(
    val chains: List<ChainUiModel> = emptyList(),
)

internal data class ChainUiModel(
    val isEnabled: Boolean,
    val coin: Coin,
)

@HiltViewModel
internal class ChainSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val discoverToken: DiscoverTokenUseCase,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.AddChainAccount>().vaultId
    val uiState = MutableStateFlow(ChainSelectionUiModel())

    val searchTextFieldState = TextFieldState()

    init {
        loadChains()
    }

    fun enableAccount(nativeToken: Coin) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: error("No vault with $vaultId")

            val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                nativeToken,
                vault
            )
            val updatedCoin = nativeToken.copy(
                address = address,
                hexPublicKey = derivedPublicKey
            )

            vaultRepository.addTokenToVault(vaultId, updatedCoin)

            loadChains()
            discoverToken(vaultId, nativeToken.chain.id)
        }
    }

    fun disableAccount(coin: Coin) {
        viewModelScope.launch {
            vaultRepository.deleteChainFromVault(vaultId, coin.chain)
            loadChains()
        }
    }

    private fun loadChains() {
        viewModelScope.launch {
            combine(
                tokenRepository.nativeTokens,
                vaultRepository.getEnabledChains(vaultId),
                searchTextFieldState.textAsFlow(),
            ) { tokens, enabledChains, query ->
                tokens
                    .filter {
                        query.isBlank() ||
                                it.ticker.contains(query, ignoreCase = true) ||
                                it.chain.raw.contains(query, ignoreCase = true)
                    }
                    .map { token ->
                        ChainUiModel(
                            isEnabled = token.chain in enabledChains,
                            coin = token,
                        )
                    }
                    .sortedWith(compareBy({ it.coin.ticker }, { it.coin.chain.raw }))

            }.collect { chains ->
                uiState.update { it.copy(chains = chains) }
            }
        }
    }

}