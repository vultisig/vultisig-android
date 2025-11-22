package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ReceiveUiModel(
    val chains: List<ChainToReceiveUiModel> = emptyList(),
)

data class ChainToReceiveUiModel(
    val name: String,
    val logo: Int,
    val ticker: String,
    val address: String,
)


@HiltViewModel
internal class ReceiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountsRepository: AccountsRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    val args = savedStateHandle.toRoute<Route.Receive>()
    val vaultId = args.vaultId
    val uiState = MutableStateFlow(ReceiveUiModel())
    val searchFieldState = TextFieldState()

    init {
        accountsRepository.loadCachedAddresses(vaultId).combine(
            searchFieldState.textAsFlow().map { it.toString() }) { addresses, searchTerm ->
            val chains = addresses.map {
                ChainToReceiveUiModel(
                    name = it.chain.raw,
                    logo = it.chain.logo,
                    address = it.address,
                    ticker = it.accounts.first { account -> account.token.isNativeToken }.token.ticker
                )
            }.filter {
                val containsTicker = it.ticker.contains(
                    other = searchTerm, ignoreCase = true
                )
                val containsChainName = it.name.contains(
                    other = searchTerm, ignoreCase = true
                )
                searchTerm.isBlank() || containsTicker || containsChainName
            }
            uiState.update {
                it.copy(
                    chains = chains
                )
            }
        }.launchIn(viewModelScope)

    }

    fun onChainClick(chain: ChainToReceiveUiModel) {
        viewModelScope.launch {
            navigator.route(
                Route.AddressQr(
                    vaultId = vaultId,
                    address = chain.address,
                    name = chain.name,
                    logo = chain.logo
                )
            )
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}
