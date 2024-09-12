@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.WorkerRepository
import com.vultisig.wallet.data.workers.TokenRefreshWorker
import com.vultisig.wallet.ui.navigation.Screen.AddChainAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val workerRepository: WorkerRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[AddChainAccount.ARG_VAULT_ID])

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
            workerRepository.discoveryTokens(vaultId, nativeToken)
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
                                it.chain.name.contains(query, ignoreCase = true)
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