@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.CustomTokenRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
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

@Immutable
internal data class TokenSelectionUiModel(
    val selectedTokens: List<TokenUiModel> = emptyList(),
    val otherTokens: List<TokenUiModel> = emptyList(),
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
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val customTokenRepository: CustomTokenRepository,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val chainId: String =
        requireNotNull(savedStateHandle[ARG_CHAIN_ID])

    private val enabledTokens = MutableStateFlow(emptySet<String>())

    private val selectedTokens = MutableStateFlow(emptyList<Coin>())
    private val otherTokens = MutableStateFlow(emptyList<Coin>())

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    @OptIn(ExperimentalFoundationApi::class)
    val searchTextFieldState = TextFieldState()

    init {
        loadTokens()
        collectTokens()
    }

    fun checkCustomToken(contractAddress: String?) {
        viewModelScope.launch {
            contractAddress?.let {
                val searchedToken = requestResultRepository.request<Coin>(contractAddress)
                enableSearchedToken(searchedToken)
            }
        }
    }

    fun enableToken(coin: Coin) {
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

            enabledTokens.update { it + updatedCoin.id }
        }
    }

    fun disableToken(coin: Coin) {
        viewModelScope.launch {
            vaultRepository.deleteTokenFromVault(vaultId, coin.id)
            enabledTokens.update { it - coin.id }
        }
    }

    fun navigateToCustomTokenScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.CustomToken(chainId))
        }
    }

    private fun loadTokens() {
        val chain = Chain.fromRaw(chainId)

        viewModelScope.launch {
            val enabled = vaultRepository.getEnabledTokens(vaultId)
                .map { enabled -> enabled.map { it.id }.toSet() }
                .first()

            enabledTokens.value = enabled

            try {
                val tokens = tokenRepository.getChainTokens(chain)
                    .map { tokens -> tokens.filter { !it.isNativeToken } }
                    .first()

                selectedTokens.value = tokens.filter { it.id in enabled }
                otherTokens.value = tokens.filter { it.id !in enabled }
            } catch (e: Exception) {
                // todo handle error
                Timber.e(e)
            }
        }
    }

    private fun collectTokens() {
        combine(
            enabledTokens,
            selectedTokens,
            otherTokens,
            searchTextFieldState.textAsFlow()
                .map { it.toString() },
        ) { enabled, selected, other, query ->
            val selectedUiTokens = selected.asUiTokens(enabled)
            val otherUiTokens = other.asUiTokens(enabled)
                .filter { it.coin.ticker.contains(query, ignoreCase = true) }

            uiState.update {
                it.copy(
                    selectedTokens = selectedUiTokens,
                    otherTokens = otherUiTokens,
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

    private fun enableSearchedToken(coin: Coin) {
        viewModelScope.launch {
            coin.apply {
                if (enabledTokens.value.contains(id))
                    return@apply
                enableToken(this)
                showTokenAsEnabled(this)
                val allTokens = tokenRepository
                    .getChainTokens(Chain.fromRaw(chainId)).first().map { it.id }
                if (!allTokens.contains(id))
                    customTokenRepository.insert(this)
            }
        }
    }


    private fun showTokenAsEnabled(token: Coin) {
        val inOtherTokens = otherTokens.value.map { it.id }.any { it == token.id }
        val updatedSelectedTokens = selectedTokens.value.toMutableList()
        if (!updatedSelectedTokens.map { it.id }.contains(token.id))
            updatedSelectedTokens += token
        selectedTokens.update {
            updatedSelectedTokens
        }
        if (inOtherTokens) {
            val updatedOtherTokens = otherTokens.value.filter { it.id != token.id }
            otherTokens.update {
                updatedOtherTokens
            }
        }
    }
}