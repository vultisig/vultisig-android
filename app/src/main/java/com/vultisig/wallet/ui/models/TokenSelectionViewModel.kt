package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class TokenSelectionUiModel(
    val tokens: List<TokenUiModel> = emptyList(),
    val tempSelections: List<TokenUiModel> = emptyList(),
)

@Immutable
internal data class TokenUiModel(
    val isEnabled: Boolean,
    val coin: Coin,
)

@HiltViewModel
internal class TokenSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val getChainTokens: GetChainTokensUseCase
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.SelectTokens>().vaultId

    private val chainId: String = savedStateHandle.toRoute<Route.SelectTokens>().chainId

    private val enabledTokenIds = MutableStateFlow(emptySet<String>())

    private val enabledTokens = MutableStateFlow(emptyList<Coin>())
    private val disabledTokens = MutableStateFlow(emptyList<Coin>())

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    val searchTextFieldState = TextFieldState()

    init {
        loadTokens()
        collectTokens()
    }

    fun checkCustomToken() {
        viewModelScope.launch {
            val searchedToken = requestResultRepository.request<Coin>(REQUEST_SEARCHED_TOKEN_ID)
            if (searchedToken != null) {
                enableSearchedToken(searchedToken)
            }
        }
    }

    fun navigateToCustomTokenScreen() {
        viewModelScope.launch {
            navigator.route(Route.CustomToken(chainId))
        }
    }

    fun enableTokenTemp(coin: Coin) {
        val tokens = uiState.value.tempSelections + TokenUiModel(
            isEnabled = true,
            coin = coin
        )
        uiState.update {
            it.copy(tempSelections = tokens)
        }
    }

    fun disableTokenTemp(coin: Coin) {
        val tokens = uiState.value.tempSelections + TokenUiModel(
            isEnabled = false,
            coin = coin
        )
        uiState.update {
            it.copy(tempSelections = tokens)
        }
    }

    fun onCommitChanges() {
        val toEnableAccounts = uiState.value.tokens.filter { it.isEnabled }
        val toDisableAccounts = uiState.value.tokens - toEnableAccounts

        viewModelScope.launch {
            toEnableAccounts.forEach {
                enableTokenUseCase(vaultId, it.coin)
            }
            toDisableAccounts.forEach {
                vaultRepository.disableTokenFromVault(vaultId, it.coin)
            }
            requestResultRepository.respond(REFRESH_TOKEN_DATA, Unit)
            navigator.back()
        }
    }

    private fun loadTokens() {
        val chain = Chain.fromRaw(chainId)

        viewModelScope.launch {
            val enabledTokens = vaultRepository
                .getEnabledTokens(vaultId)
                .first()
                .filter { !it.isNativeToken && it.chain == chain }

            this@TokenSelectionViewModel.enabledTokens.value = enabledTokens

            val enabledTokenIds = enabledTokens.map { it.id }.toSet()
            this@TokenSelectionViewModel.enabledTokenIds.value = enabledTokenIds

            try {
                val vault = vaultRepository.get(vaultId) ?: error("No vault with id $vaultId")
                val allChainTokens = getChainTokens(chain, vault)
                    .map { tokens -> tokens.filter { !it.isNativeToken } }
                val enabledTokenIdsLowercase = enabledTokenIds.map { tokenId ->
                    tokenId.lowercase()
                }
                allChainTokens.collect { allChains ->
                    disabledTokens.value = allChains.filter { coin ->
                        coin.id.lowercase() !in enabledTokenIdsLowercase
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun collectTokens() {
        combine(
            uiState.map { it.tempSelections }.distinctUntilChanged(),
            enabledTokenIds,
            enabledTokens,
            disabledTokens,
            searchTextFieldState.textAsFlow()
                .map { it.toString() },
        ) { tempSelections, enabledTokenIds, enabledTokens, disabledTokens, query ->
            val selectedUiTokens = enabledTokens
                .filter { it.ticker.contains(query, ignoreCase = true) }
                .asUiTokens(enabledTokenIds)

            val otherUiTokens = if (query.isNotBlank()) {
                disabledTokens.filter { it.ticker.contains(query, ignoreCase = true) }
            } else {
                disabledTokens
            }.asUiTokens(enabledTokenIds)

            val tokens = (selectedUiTokens + otherUiTokens + tempSelections)
                .associateBy(TokenUiModel::coin)
                .values
                .toList()

            uiState.update { uiState ->
                uiState.copy(
                    tokens = tokens
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
        .toList()

    private fun enableSearchedToken(coin: Coin) {
        viewModelScope.launch {
            coin.apply {
                if (enabledTokenIds.value.contains(id))
                    return@apply
                enableTokenUseCase(vaultId, this)
                loadTokens()
            }
        }
    }

    fun back(){
        viewModelScope.launch {
            navigator.back()
        }
    }

    companion object {
        const val REQUEST_SEARCHED_TOKEN_ID = "request_searched_token_id"
        const val REFRESH_TOKEN_DATA = "refresh_token_data"
    }
}