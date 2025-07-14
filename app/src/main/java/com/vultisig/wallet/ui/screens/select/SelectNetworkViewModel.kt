@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SelectNetworkUiModel(
    val selectedNetwork: Chain = Chain.ThorChain,
    val networks: List<NetworkUiModel> = emptyList(),
)

internal data class NetworkUiModel(
    val chain: Chain,
    val logo: ImageModel,
    val title: String,
)


@HiltViewModel
internal class SelectNetworkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,

    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectNetwork>()

    private val vaultId = args.vaultId
    private val selectedNetwork = Chain.fromRaw(args.selectedNetworkId)

    val searchFieldState = TextFieldState()

    val state = MutableStateFlow(
        SelectNetworkUiModel(
            selectedNetwork = selectedNetwork,
        )
    )

    init {
        collectSearchResults()
    }

    fun selectNetwork(model: NetworkUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, model.chain)
            navigator.navigate(Destination.Back)
        }
    }

    fun back() {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, selectedNetwork)
            navigator.navigate(Destination.Back)
        }
    }

    private fun collectSearchResults() {
        combine(
            vaultRepository.getEnabledChains(vaultId),
            searchFieldState.textAsFlow()
                .map { it.toString() },
        ) { chains, query ->
            val filteredChains = chains
                .asSequence()
                .filter {
                    it.raw.contains(query, ignoreCase = true)
                            || it.coinType.symbol.contains(query, ignoreCase = true)
                }
                .let {
                    when (args.filters) {
                        Filters.SwapAvailable ->
                            it.filter { it.IsSwapSupported }

                        Filters.DisableNetworkSelection ->
                            it.filter { it.id == selectedNetwork.id }

                        Filters.None -> it
                    }
                }
                .sortedWith(compareBy { it.raw })
                .map {
                    NetworkUiModel(
                        chain = it,
                        logo = it.logo,
                        title = it.raw,
                    )
                }
                .toList()

            this.state.update {
                it.copy(networks = filteredChains)
            }
        }.launchIn(viewModelScope)
    }

}