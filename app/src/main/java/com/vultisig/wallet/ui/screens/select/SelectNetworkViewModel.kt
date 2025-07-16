package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

internal data class SelectNetworkUiModel(
    val selectedNetwork: Chain = Chain.ThorChain,
    val networks: List<NetworkUiModel> = emptyList(),
)

internal data class NetworkUiModel(
    val chain: Chain,
    val logo: ImageModel,
    val title: String,
    val value: FiatValue? = null,
)


@HiltViewModel
internal class SelectNetworkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val accountRepository: AccountsRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectNetwork>()

    private val vaultId = args.vaultId
    private val selectedNetwork = Chain.fromRaw(args.selectedNetworkId)
    private var balanceCaches: List<NetworkUiModel>? = null

    val searchFieldState = TextFieldState()

    val state = MutableStateFlow(
        SelectNetworkUiModel(
            selectedNetwork = selectedNetwork,
        )
    )

    init {
        collectSearchResults()
        loadBalances()
    }

    private fun loadBalances() {
        viewModelScope.launch {
            accountRepository.loadAddresses(
                vaultId = vaultId,
            ).catch {
                Timber.e(it)
            }.collect { addresses ->
                supervisorScope {
                    val result = addresses.map { address ->
                        async {
                            val totalFiatValue = address.accounts.calculateAccountsTotalFiatValue()
                            address.chain to totalFiatValue
                        }
                    }.awaitAll()

                }
            }
        }
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
                .map { chain ->
                    NetworkUiModel(
                        chain = chain,
                        logo = chain.logo,
                        title = chain.raw,
                    )
                }
                .toList()

            this.state.update {
                it.copy(networks = filteredChains)
            }

            if (balanceCaches == null) {
                accountRepository.loadAddresses(vaultId = vaultId)
                    .catch { Timber.e(it) }
                    .collect { addresses ->
                        val filteredChainsWithBalance = coroutineScope {
                            addresses.map { address ->
                                async {
                                    val totalFiatValue =
                                        address.accounts.calculateAccountsTotalFiatValue()
                                    NetworkUiModel(
                                        chain = address.chain,
                                        logo = address.chain.logo,
                                        title = address.chain.raw,
                                        value = totalFiatValue,
                                    )
                                }
                            }
                        }.awaitAll()

                        val chainsWithPrice =
                            filteredChainsWithBalance.mapNotNull { filteredChainWithBalance ->
                                val chain = filteredChainWithBalance.chain
                                filteredChains.find { it.chain == chain }
                                    ?.copy(value = filteredChainWithBalance.value)
                            }


                        balanceCaches = chainsWithPrice

                        this.state.update {
                            it.copy(networks = chainsWithPrice)
                        }
                    }
            }
        }.launchIn(viewModelScope)
    }
}