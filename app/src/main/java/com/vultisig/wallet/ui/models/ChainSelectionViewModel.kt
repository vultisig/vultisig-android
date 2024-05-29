package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ChainsOrderRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.navigation.Screen.AddChainAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
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
    private val chainsOrderRepository: ChainsOrderRepository,
    ) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[AddChainAccount.ARG_VAULT_ID])

    val uiState = MutableStateFlow(ChainSelectionUiModel())

    init {
        loadChains()
    }

    fun enableAccount(coin: Coin) {
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
            chainsOrderRepository.insert(updatedCoin.chain.raw)
            loadChains()
        }
    }

    fun disableAccount(coin: Coin) {
        viewModelScope.launch {
            vaultRepository.deleteChainFromVault(vaultId, coin.chain)
            chainsOrderRepository.delete(coin.chain.raw)
            loadChains()
        }
    }

    private fun loadChains() {
        viewModelScope.launch {
            tokenRepository.nativeTokens
                .zip(vaultRepository.getEnabledChains(vaultId)) { native, enabledChains ->
                    native
                        .sortedWith(compareBy({ it.ticker }, { it.chain.raw }))
                        .map { nativeToken ->
                            ChainUiModel(
                                isEnabled = nativeToken.chain in enabledChains,
                                coin = nativeToken,
                            )
                        }
                }.collect { chains ->
                    uiState.update { it.copy(chains = chains) }
                }
        }
    }

}