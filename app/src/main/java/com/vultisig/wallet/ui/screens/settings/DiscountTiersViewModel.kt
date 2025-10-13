package com.vultisig.wallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal data class DiscountTiersUiModel(
    val activeTier: TierType? = null,
)

@HiltViewModel
internal class DiscountTiersViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
) : ViewModel() {

    fun navigateToSwaps(navController: NavHostController, vaultId: String) {
        viewModelScope.launch {
            try {
                // First ensure Ethereum chain is enabled, if not make sure native coin is enabled
                val vault = withContext(Dispatchers.IO) {
                    vaultRepository.get(vaultId)
                }
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
                    
                    // Check and enable VULT token
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
}