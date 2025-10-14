package com.vultisig.wallet.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
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
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
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
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }

                if (vault != null) {
                    // Check if VULT token is enabled
                    val vultCoin = vault.coins.find { it.id == Coins.Ethereum.VULT.id }

                    if (vultCoin != null) {
                        // Get the Ethereum address for this vault
                        val (address, _) = chainAccountAddressRepository.getAddress(
                            Chain.Ethereum,
                            vault
                        )

                        // Get VULT balance from cache first
                        val vultBalanceCache = balanceRepository.getCachedTokenBalances(
                            listOf(address),
                            listOf(vultCoin)
                        ).find { it.coinId == Coins.Ethereum.VULT.id }

                        val cachedVultBalance = vultBalanceCache?.tokenBalance?.tokenValue?.value
                        
                        // Update UI with cached value if available
                        if (cachedVultBalance != null) {
                            val cachedTier = cachedVultBalance.determineTier()
                            _state.value = DiscountTiersUiModel(
                                activeTier = cachedTier,
                                isLoading = false
                            )
                            if (cachedTier != null) {
                                expandOrCollapseTierInfo(cachedTier)
                            }
                            Timber.d("VULT cached balance: $cachedVultBalance, Active tier: $cachedTier")
                        }

                        // Fetch fresh balance from network
                        try {
                            val freshTokenValue = balanceRepository.getTokenValue(
                                address,
                                vultCoin
                            ).first() // Collect first emission from the Flow
                            
                            val vultBalance = freshTokenValue.value
                            val tier = vultBalance.determineTier()

                            _state.value = DiscountTiersUiModel(
                                activeTier = tier,
                                isLoading = false
                            )

                            if (tier != null) {
                                expandOrCollapseTierInfo(tier)
                            }

                            Timber.d("VULT fresh balance: $vultBalance, Active tier: $tier")
                        } catch (e: Exception) {
                            Timber.e(e)
                            if (cachedVultBalance == null) {
                                throw e
                            }
                        }
                    } else {
                        _state.value = DiscountTiersUiModel(
                            activeTier = null,
                            isLoading = false
                        )
                    }
                } else {
                    _state.value = DiscountTiersUiModel(isLoading = false, activeTier = null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading VULT balance")
                _state.value = DiscountTiersUiModel(isLoading = false, activeTier = null)
            }
        }
    }

    private fun BigInteger.determineTier(): TierType? {
        return when {
            this >= PLATINUM_TIER_THRESHOLD -> TierType.PLATINIUM
            this >= GOLD_TIER_THRESHOLD -> TierType.GOLD
            this >= SILVER_TIER_THRESHOLD -> TierType.SILVER
            this >= BRONZE_TIER_THRESHOLD -> TierType.BRONZE
            else -> null
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