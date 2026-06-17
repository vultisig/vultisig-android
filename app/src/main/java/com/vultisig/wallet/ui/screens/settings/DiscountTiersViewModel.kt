package com.vultisig.wallet.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.TierRemoteNFTService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.DIAMOND_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.ULTIMATE_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.sVultCoin
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class DiscountTiersUiModel(
    val activeTier: TierType? = null,
    val tierClicked: TierType? = null,
    val isLoading: Boolean = true,
    val showBottomSheetDialog: Boolean = false,
)

@HiltViewModel
internal class DiscountTiersViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val balanceRepository: BalanceRepository,
    private val tiersNFTRepository: TiersNFTRepository,
    private val remoteNFTService: TierRemoteNFTService,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.DiscountTiers>().vaultId

    private val _state = MutableStateFlow(DiscountTiersUiModel())
    val state: StateFlow<DiscountTiersUiModel> = _state.asStateFlow()

    init {
        loadTierType()
    }

    private fun loadTierType() {
        viewModelScope.launch {
            try {
                val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }

                if (vault != null) {
                    // Tier is determined by staked VULT (sVULT), not the raw VULT balance, so this
                    // does not gate on the vault holding VULT — every vault has an Ethereum
                    // address.
                    val (address, _) =
                        chainAccountAddressRepository.getAddress(Chain.Ethereum, vault)

                    // Get staked balance from cache first
                    val stakedBalanceCache =
                        balanceRepository
                            .getCachedTokenBalances(listOf(address), listOf(sVultCoin))
                            .find { it.coinId == sVultCoin.id }

                    val hasNFTCache = tiersNFTRepository.hasTierNFT(vaultId)

                    val cachedStakedBalance = stakedBalanceCache?.tokenBalance?.tokenValue?.value

                    // Update UI with cached value if available
                    if (cachedStakedBalance != null) {
                        val cachedTier =
                            cachedStakedBalance.determineTier()?.applyExtraDiscount(hasNFTCache)
                        _state.value =
                            DiscountTiersUiModel(activeTier = cachedTier, isLoading = false)
                        Timber.d(
                            "sVULT cached balance: %s, Active tier: %s",
                            cachedStakedBalance,
                            cachedTier,
                        )
                    }

                    // Fetch fresh balance from network
                    try {
                        val freshTokenValue =
                            balanceRepository
                                .getTokenValue(address, sVultCoin)
                                .first() // Collect first emission from the Flow

                        val hasNFTValue =
                            withContext(Dispatchers.IO) {
                                remoteNFTService.checkNFTBalance(address)
                            }
                        val stakedBalance = freshTokenValue.value
                        val tier = stakedBalance.determineTier()?.applyExtraDiscount(hasNFTValue)

                        _state.value = DiscountTiersUiModel(activeTier = tier, isLoading = false)

                        Timber.d("sVULT fresh balance: %s, Active tier: %s", stakedBalance, tier)

                        withContext(Dispatchers.IO) {
                            tiersNFTRepository.saveTierNFT(vaultId, hasNFTValue)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.e(e)
                        if (cachedStakedBalance == null) {
                            throw e
                        }
                    }
                } else {
                    _state.value = DiscountTiersUiModel(isLoading = false, activeTier = null)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Error loading VULT balance")
                _state.value = DiscountTiersUiModel(isLoading = false, activeTier = null)
            }
        }
    }

    private fun BigInteger.determineTier(): TierType? {
        return when {
            this >= ULTIMATE_TIER_THRESHOLD -> TierType.ULTIMATE
            this >= DIAMOND_TIER_THRESHOLD -> TierType.DIAMOND
            this >= PLATINUM_TIER_THRESHOLD -> TierType.PLATINUM
            this >= GOLD_TIER_THRESHOLD -> TierType.GOLD
            this >= SILVER_TIER_THRESHOLD -> TierType.SILVER
            this >= BRONZE_TIER_THRESHOLD -> TierType.BRONZE
            else -> null
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun navigateToSwaps() {
        viewModelScope.launch {
            try {
                val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
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
                            if (e is kotlinx.coroutines.CancellationException) throw e
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
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e, "Failed to enable VULT token")
                        }
                    }
                }

                navigator.route(
                    Route.Swap(
                        vaultId = vaultId,
                        chainId = Chain.Ethereum.id,
                        srcTokenId = null,
                        dstTokenId = Coins.Ethereum.VULT.id,
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Error preparing swap navigation")
                navigator.route(
                    Route.Swap(
                        vaultId = vaultId,
                        chainId = Chain.Ethereum.id,
                        srcTokenId = null,
                        dstTokenId = null,
                    )
                )
            }
        }
    }

    fun onTierUnlockClick(tier: TierType) {
        _state.update { it.copy(tierClicked = tier, showBottomSheetDialog = true) }
    }

    fun dismissBottomSheet() {
        _state.update { it.copy(tierClicked = null, showBottomSheetDialog = false) }
    }
}
