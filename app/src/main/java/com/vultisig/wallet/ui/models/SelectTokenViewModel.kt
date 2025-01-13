package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_SWAP_SELECT
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_TARGET_ARG
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.textAsFlow
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
    private val requestResultRepository: RequestResultRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val targetArg: String =
        requireNotNull(savedStateHandle[ARG_TARGET_ARG])


    private val swapSelect: Boolean = savedStateHandle[ARG_SWAP_SELECT]?: false

    private val selectedTokens = MutableStateFlow(emptyList<Coin>())

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    
    val searchTextFieldState = TextFieldState()

    init {
        loadTokens()
        collectTokens()
    }

    fun enableToken(token: Coin) {
        viewModelScope.launch {
            requestResultRepository.respond(targetArg, token)
            navigator.navigate(Destination.Back)
        }
    }

    private fun loadTokens() {
        viewModelScope.launch {

            val enabled = vaultRepository.getEnabledTokens(vaultId).first()

            val enabledFiltered =
                if (swapSelect)
                    enabled.filter { it.chain.IsSwapSupported }
                else
                    enabled

            try {
                selectedTokens.value = enabledFiltered
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
                .filter { it.coin.id.contains(query, ignoreCase = true) }


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