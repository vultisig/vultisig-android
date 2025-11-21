package com.vultisig.wallet.ui.screens.v2.receive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.searchbar.SearchBar
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.theme.Theme
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
    private val accountsRepository: AccountsRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    val args = savedStateHandle.toRoute<Route.Receive>()
    val vaultId = args.vaultId
    val uiState = MutableStateFlow(ReceiveUiModel())
    val searchFieldState = TextFieldState()

    init {
        viewModelScope.launch {
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
            }.launchIn(this)

        }
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

@Composable
internal fun ReceiveBottomSheet(
    viewModel: ReceiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    V2BottomSheet (
        onDismissRequest = viewModel::back
    ) {
        ReceiveContent(
            searchFieldState = viewModel.searchFieldState,
            uiState = uiState,
            onChainClick = viewModel::onChainClick
        )

    }

}

@Composable
private fun ReceiveContent(
    uiState: ReceiveUiModel,
    searchFieldState: TextFieldState,
    onChainClick: (ChainToReceiveUiModel)-> Unit
) {
    Column(
        Modifier.fillMaxSize()
    ) {
        SearchBar(
            isInitiallyFocused = false,
            state = searchFieldState,
            onCancelClick = {},
        )
        LazyColumn() {
            items(uiState.chains) {
                Text(
                    text = it.name,
                    modifier = Modifier.clickable {
                        onChainClick(it)
                    },
                    color = Theme.colors.text.primary
                )
            }
        }
    }


}