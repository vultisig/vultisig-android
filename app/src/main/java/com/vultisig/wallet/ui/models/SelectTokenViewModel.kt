@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_SWAP_SELECT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class SelectTokenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val swapSelect: Boolean = savedStateHandle[ARG_SWAP_SELECT]?: false

    private val selectedTokens = MutableStateFlow(emptyList<Coin>())

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    @OptIn(ExperimentalFoundationApi::class)
    val searchTextFieldState = TextFieldState()

    init {
        loadTokens()
        collectTokens()
    }

    private fun loadTokens() {
        viewModelScope.launch {

            val enabled =
                if (swapSelect)
                    vaultRepository.getEnabledTokens(vaultId)
                        .first()
                        .filter { it.chain.IsSwapSupported }
                else
                    vaultRepository.getEnabledTokens(vaultId)
                        .first()

            try {
                selectedTokens.value = enabled
            } catch (e: Exception) {
                // todo handle error
                Timber.e(e)
            }
        }
    }

    private fun collectTokens() {
        combine(
            selectedTokens,
            searchTextFieldState.textAsFlow()
                .map { it.toString() },
        ) { selected, query ->
            val selectedUiTokens = selected.asUiTokens(emptySet())
                .filter { it.coin.ticker.contains(query, ignoreCase = true) }


            uiState.update {
                it.copy(
                    selectedTokens = selectedUiTokens,
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun List<Coin>.asUiTokens(enabled: Set<String>) = asSequence()
        .map { token ->
            TokenUiModel(
                isEnabled = token.id in enabled,
                coin = token,
            )
        }
        .sortedWith(compareBy { it.coin.ticker })
        .toList()

}