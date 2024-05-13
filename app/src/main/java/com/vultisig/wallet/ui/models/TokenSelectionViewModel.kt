package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val vaultDb: VaultDB,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[Destination.SelectTokens.ARG_VAULT_ID])

    private val chainId: String =
        requireNotNull(savedStateHandle[Destination.SelectTokens.ARG_CHAIN_ID])

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    init {
        loadChains()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun enableToken(coin: Coin) {
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

    fun disableToken(coin: Coin) {
        commitVault(checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }.let { vault ->
            vault.copy(coins = vault.coins.filter { it.id != coin.id })
        })
    }

    private fun commitVault(vault: Vault) {
        vaultDb.upsert(vault)
        loadChains()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val coins = Coins.SupportedCoins
                .filter { it.chain.raw == chainId }

            val enabledTokens = vaultDb.select(vaultId)
                ?.coins
                ?.map { it.id }
                ?.toSet()
                ?: emptySet()

            val chains = coins
                .map { coin ->
                    TokenUiModel(
                        isEnabled = coin.id in enabledTokens,
                        coin = coin,
                    )
                }

            uiState.update { it.copy(tokens = chains) }
        }
    }

}