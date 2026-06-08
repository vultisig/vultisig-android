package com.vultisig.wallet.ui.models.customrpc

import androidx.annotation.DrawableRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.CustomRpcSupportedChains
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.CustomRpcRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CustomRpcRowUiModel(
    val chainId: String,
    val chainName: String,
    @DrawableRes val logo: Int,
    val customUrl: String?,
) {
    val isCustom: Boolean
        get() = customUrl != null
}

internal data class CustomRpcListUiState(
    val isSilver: Boolean = false,
    val rows: List<CustomRpcRowUiModel> = emptyList(),
)

@HiltViewModel
internal class CustomRpcListViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val customRpcRepository: CustomRpcRepository,
    private val getDiscountBps: GetDiscountBpsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.CustomRpcList>().vaultId

    val state = MutableStateFlow(CustomRpcListUiState())

    init {
        viewModelScope.launch {
            val isSilver =
                runCatching { getDiscountBps.hasReachedSilverTier(vaultId) }.getOrDefault(false)
            state.update { it.copy(isSilver = isSilver) }
        }
        viewModelScope.launch {
            customRpcRepository.overrides.collect { overrides ->
                val rows =
                    CustomRpcSupportedChains.all.map { chain ->
                        CustomRpcRowUiModel(
                            chainId = chain.id,
                            chainName = chain.raw,
                            logo = chain.logo,
                            customUrl = overrides[chain],
                        )
                    }
                state.update { it.copy(rows = rows) }
            }
        }
    }

    fun onRowClick(chainId: String) {
        viewModelScope.launch {
            // Tap-to-upsell: below Silver tier the row routes to the discount-tiers screen instead
            // of the editor (#4787).
            if (state.value.isSilver) {
                navigator.route(Route.CustomRpcDetail(vaultId, chainId))
            } else {
                navigator.route(Route.DiscountTiers(vaultId))
            }
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
