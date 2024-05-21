package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
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
    private val vaultDb: VaultDB,
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[AddChainAccount.ARG_VAULT_ID])

    val uiState = MutableStateFlow(ChainSelectionUiModel())

    init {
        loadChains()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun enableAccount(coin: Coin) {
        viewModelScope.launch {
            commitVault(checkNotNull(vaultDb.select(vaultId)) {
                "No vault with $vaultId"
            }.let { vault ->
                val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
                    coin,
                    vault
                )
                vault.copy(
                    coins = vault.coins + coin.copy(
                        address = address,
                        hexPublicKey = derivedPublicKey
                    )
                )
            })
        }
    }

    fun disableAccount(coin: Coin) {
        commitVault(checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }.let { vault ->
            vault.copy(coins = vault.coins.filter { it.chain != coin.chain })
        })
    }

    private fun commitVault(vault: Vault) {
        vaultDb.upsert(vault)
        loadChains()
    }

    private fun loadChains() {
        viewModelScope.launch {
            tokenRepository.nativeTokens
                .zip(tokenRepository.getEnabledChains(vaultId)) { native, enabledChains ->
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