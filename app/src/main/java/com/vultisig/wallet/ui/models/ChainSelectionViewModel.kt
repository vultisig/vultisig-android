package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Screen.VaultDetail.AddChainAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val vaultDb: VaultDB,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[AddChainAccount.ARG_VAULT_ID])

    val uiState = MutableStateFlow(ChainSelectionUiModel())

    init {
        loadChains()
    }

    fun enableAccount(coin: Coin) {
        val vault = checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }

        vault.coins.add(coin)
        commitVault(vault)
    }

    fun disableAccount(coin: Coin) {
        val vault = checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }

        vault.coins.removeIf { it.chain == coin.chain }
        commitVault(vault)
    }

    private fun commitVault(vault: Vault) {
        vaultDb.upsert(vault)
        loadChains()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val coins = Coins.SupportedCoins
            val enabledChains = vaultDb.select(vaultId)
                ?.coins
                ?.filter { it.isNativeToken }
                ?.map { it.chain }
                ?.toSet()
                ?: emptySet()

            val chains = coins
                .filter { it.isNativeToken }
                .map { coin ->
                    ChainUiModel(
                        isEnabled = coin.chain in enabledChains,
                        coin = coin,
                    )
                }

            uiState.update { it.copy(chains = chains) }
        }
    }

}