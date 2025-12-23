package com.vultisig.wallet.ui.components.fastselection

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.models.NetworkUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SelectNetworkPopupSharedUiModel(
    val networks: List<NetworkUiModel> = emptyList(),
    val isLongPressActive: Boolean = false,
    val currentDragPosition: Offset? = null,
    val vaultId: VaultId = "",
)


@HiltViewModel
internal class FastSelectionPopupSharedViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {


    val uiState = MutableStateFlow(SelectNetworkPopupSharedUiModel())
    lateinit var networkArgs: Route.SelectNetworkPopup


    fun loadNetworkData() {
        val vaultId = networkArgs.vaultId
        val selectedNetwork = Chain.fromRaw(networkArgs.selectedNetworkId)

        vaultRepository.getEnabledChains(vaultId)
            .onEach { chains ->
                val networks = chains
                    .filter { chains ->
                        when (networkArgs.filters) {
                            Route.SelectNetwork.Filters.SwapAvailable -> chains.isSwapSupported
                            Route.SelectNetwork.Filters.DisableNetworkSelection -> chains.id == selectedNetwork.id
                            Route.SelectNetwork.Filters.None -> true
                        }
                    }.map { chain ->
                        NetworkUiModel(
                            chain = chain,
                            logo = chain.logo,
                            title = chain.raw,
                            value = ""
                        )
                    }

                uiState.update {
                    it.copy(
                        networks = networks,
                    )
                }
            }
            .launchIn(viewModelScope)
    }



    fun initNetworks(args: Route.SelectNetworkPopup) {
        networkArgs = args
        loadNetworkData()
    }




    fun onNetworkSelected(networkUiModel: NetworkUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(networkArgs.requestId, networkUiModel.chain)
            navigator.back()
        }
    }


    fun onDragStart(position: Offset) {
        uiState.update {
            it.copy(
                isLongPressActive = true,
                currentDragPosition = position
            )
        }
    }

    fun onDrag(position: Offset) {
        uiState.update {
            it.copy(
                currentDragPosition = position
            )
        }
    }

    fun onDragEnd() {
        uiState.update {
            it.copy(
                isLongPressActive = false,
                currentDragPosition = null
            )
        }
    }
}
