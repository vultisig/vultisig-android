package com.vultisig.wallet.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal data class DiscountTiersUiModel(
    val activeTier: TierType? = null,
    val tierClicked: TierType? = null,
    val expandedTiers: Set<TierType> = emptySet(),
    val isLoading: Boolean = true,
    val showBottomSheetDialog: Boolean = false,
)

@HiltViewModel
internal class DiscountTiersViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle.get<String>(ARG_VAULT_ID))
    
    private val _state = MutableStateFlow(DiscountTiersUiModel())
    val state: StateFlow<DiscountTiersUiModel> = _state.asStateFlow()

    init {
        loadTierType()
    }

    private fun loadTierType() {
        viewModelScope.launch {
            try {
                // Use the use case to get the tier directly
                val tier = withContext(Dispatchers.IO) {
                    getDiscountBpsUseCase.getTier(vaultId)
                }
                
                val discountBps = withContext(Dispatchers.IO) {
                    getDiscountBpsUseCase(vaultId)
                }
                
                _state.value = DiscountTiersUiModel(
                    activeTier = tier,
                    isLoading = false
                )

                if (tier != null) {
                    expandOrCollapseTierInfo(tier)
                }
                
                Timber.d("Active tier: $tier, Discount: $discountBps BPS")
            } catch (e: Exception) {
                Timber.e(e, "Error loading VULT tier")
                _state.value = DiscountTiersUiModel(isLoading = false, activeTier = null)
            }
        }
    }

    fun navigateToSwaps(navController: NavHostController, vaultId: String) {
        viewModelScope.launch {
            try {
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }
                // First ensure Ethereum chain is enabled, if not make sure native coin is enabled
                if (vault != null) {
                    val hasEthereum = vault.coins.any { it.chain == Chain.Ethereum }
                    if (!hasEthereum) {
                        try {
                            withContext(Dispatchers.IO) {
                                enableTokenUseCase(vaultId, Coins.Ethereum.ETH)
                            }
                            Timber.d("Ethereum chain enabled successfully")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to enable Ethereum chain")
                        }
                    }
                    
                    // Check and enable VULT token, if not enable it
                    val hasVult = vault.coins.any { it.id == Coins.Ethereum.VULT.id }
                    if (!hasVult) {
                        try {
                            withContext(Dispatchers.IO) {
                                enableTokenUseCase(vaultId, Coins.Ethereum.VULT)
                            }
                            Timber.d("VULT token enabled successfully")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to enable VULT token")
                        }
                    }
                }

                navController.navigate(Route.Swap(
                    vaultId = vaultId,
                    chainId = Chain.Ethereum.id,
                    srcTokenId = null,
                    dstTokenId = Coins.Ethereum.VULT.id
                ))
            } catch (e: Exception) {
                Timber.e(e, "Error preparing swap navigation")
                navController.navigate(Route.Swap(
                    vaultId = vaultId,
                    chainId = Chain.Ethereum.id,
                    srcTokenId = null,
                    dstTokenId = null
                ))
            }
        }
    }

    fun expandOrCollapseTierInfo(tier: TierType) {
        _state.update { current ->
            current.copy(
                expandedTiers = if (current.expandedTiers.contains(tier)) {
                    current.expandedTiers - tier  // Collapse
                } else {
                    current.expandedTiers + tier  // Expand
                }
            )
        }
    }

    fun onTierUnlockClick(tier: TierType) {
        _state.update {
            it.copy(
                tierClicked = tier,
                showBottomSheetDialog = true
            )
        }
    }

    fun dismissBottomSheet() {
        _state.update {
            it.copy(
                tierClicked = null,
                showBottomSheetDialog = false
            )
        }
    }
}