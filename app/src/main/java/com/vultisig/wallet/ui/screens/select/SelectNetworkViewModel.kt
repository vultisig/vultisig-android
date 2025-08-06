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
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

internal data class SelectNetworkUiModel(
    val selectedNetwork: Chain = Chain.ThorChain,
    val networks: List<NetworkUiModel> = emptyList(),
)

data class NetworkUiModel(
    val chain: Chain,
    val logo: ImageModel,
    val title: String,
    val value: String? = null,
)

@HiltViewModel
internal class SelectNetworkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val accountRepository: AccountsRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
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
            searchFieldState.textAsFlow().map { it.toString() },
            loadAddressesWithBalances(),
        ) { chains, query, chainBalances ->
            val filteredChains = chains
                .asSequence()
                .filter { chain ->
                    val matchesQuery = chain.raw.contains(query, ignoreCase = true) ||
                            chain.coinType.symbol.contains(query, ignoreCase = true)
                    val matchesFilter = when (args.filters) {
                        Filters.SwapAvailable -> chain.IsSwapSupported
                        Filters.DisableNetworkSelection -> chain.id == selectedNetwork.id
                        Filters.None -> true
                    }
                    matchesQuery && matchesFilter
                }
                .sortedWith(compareBy { it.raw })
                .map { chain ->
                    NetworkUiModel(
                        chain = chain,
                        logo = chain.logo,
                        title = chain.raw,
                        value = chainBalances[chain] ?: ""
                    )
                }
                .toList()

            this.state.update {
                it.copy(networks = filteredChains)
            }
        }.launchIn(viewModelScope)
    }

    private fun loadAddressesWithBalances(): Flow<Map<Chain, String>> {
        return accountRepository.loadCachedAddresses(vaultId = vaultId)
            .catch {
                Timber.e(it)
                emit(emptyList())
            }
            .map { addresses ->
                coroutineScope {
                    addresses.map { address ->
                        async {
                            val totalFiatValue = address.accounts.calculateAccountsTotalFiatValue()
                                ?: FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)
                            val formattedValue = fiatValueToStringMapper(totalFiatValue)
                            address.chain to formattedValue
                        }
                    }.awaitAll().associate { it.first to it.second }
                }
            }
    }
}