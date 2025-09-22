package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.models.VaultAccountsViewModel.Companion.REFRESH_CHAIN_DATA
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
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
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.AddChainAccount>().vaultId
    val uiState = MutableStateFlow(ChainSelectionUiModel())

    val searchTextFieldState = TextFieldState()

    init {
        loadChains()
    }

    fun enableAccountTemp(nativeToken: Coin) {
        val chains = uiState.value.chains.map {
            if (it.coin.id == nativeToken.id) {
                it.copy(isEnabled = true)
            } else {
                it
            }
        }
        uiState.update {
            it.copy(chains = chains)
        }
    }

    fun disableAccountTemp(nativeToken: Coin) {
        val chains = uiState.value.chains.map {
            if (it.coin.id == nativeToken.id) {
                it.copy(isEnabled = false)
            } else {
                it
            }
        }
        uiState.update {
            it.copy(chains = chains)
        }
    }

    fun onCommitChanges() {
        val toEnableAccounts = uiState.value.chains.filter { it.isEnabled }
        val toDisableAccounts = uiState.value.chains - toEnableAccounts

        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: error("No vault with $vaultId")

            toEnableAccounts.forEach {
                enableAccount(it.coin, vault)
            }
            toDisableAccounts.forEach {
                disableAccount(it.coin)
            }
            requestResultRepository.respond(REFRESH_CHAIN_DATA, Unit)
            navigator.back()
        }
    }

    fun cancelChanges(){
        viewModelScope.launch {
            navigator.back()
        }
    }


    private suspend fun enableAccount(nativeToken: Coin, vault: Vault) {
        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
            nativeToken,
            vault
        )
        val updatedCoin = nativeToken.copy(
            address = address,
            hexPublicKey = derivedPublicKey
        )

        vaultRepository.addTokenToVault(vaultId, updatedCoin)

        discoverToken(vaultId, nativeToken.chain.id)
    }

    private suspend fun disableAccount(coin: Coin) {
        vaultRepository.deleteChainFromVault(vaultId, coin.chain)
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