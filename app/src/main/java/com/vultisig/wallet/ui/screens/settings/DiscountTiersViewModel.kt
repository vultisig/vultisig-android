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
import com.vultisig.wallet.data.utils.toUnit
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType
import java.math.BigInteger
import javax.inject.Inject

internal data class DiscountTiersUiModel(
    val activeTier: TierType? = null,
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
                        
                        // Get VULT balance
                        val balanceAndPrice = balanceRepository.getTokenBalanceAndPrice(
                            address,
                            vultCoin
                        ).first()
                        
                        val vultBalance = balanceAndPrice.tokenBalance.tokenValue?.value ?:
                            error("Can't fetch vult balance")
                        val tier = determineTier(vultBalance)
                        
                        _state.value = DiscountTiersUiModel(
                            activeTier = tier,
                            isLoading = false
                        )
                        
                        Timber.d("VULT balance: $vultBalance, Active tier: $tier")
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

    private fun determineTier(balance: BigInteger): TierType? {
        return when {
            balance >= PLATINUM_TIER_THRESHOLD -> TierType.PLATINIUM
            balance >= GOLD_TIER_THRESHOLD -> TierType.GOLD
            balance >= SILVER_TIER_THRESHOLD -> TierType.SILVER
            balance >= BRONZE_TIER_THRESHOLD -> TierType.BRONZE
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

    private companion object {
        // $VULT has same decimals as ETH (18)
        private val BRONZE_TIER_THRESHOLD = CoinType.ETHEREUM.toUnit("1000".toBigInteger())
        private val SILVER_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("5000".toBigInteger())
        private val GOLD_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("10000".toBigInteger())
        private val PLATINUM_TIER_THRESHOLD =  CoinType.ETHEREUM.toUnit("50000".toBigInteger())
    }
}