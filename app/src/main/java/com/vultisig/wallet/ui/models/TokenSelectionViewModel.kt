package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
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
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val chainId: String =
        requireNotNull(savedStateHandle[ARG_CHAIN_ID])

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    init {
        loadChains()
    }

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
            tokenRepository.getChainTokens(vaultId, chainId)
                .zip(
                    tokenRepository.getEnabledTokens(vaultId)
                        .map { enabled -> enabled.map { it.id }.toSet() }
                ) { tokens, enabledTokens ->
                    tokens.map { token ->
                        TokenUiModel(
                            isEnabled = token.id in enabledTokens,
                            coin = token,
                        )
                    }
                }.collect { tokens ->
                    uiState.update { it.copy(tokens = tokens) }
                }
        }
    }

}