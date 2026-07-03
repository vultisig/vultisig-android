package com.vultisig.wallet.ui.models.qbtc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.hasValidMldsaKey
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class QuantumSecurityIntroViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.QuantumSecurityIntro>().vaultId

    // Mirrors iOS QuantumSecurityIntroScreen.onGetStarted and the dilithium keygen
    // entry in VaultSettingsViewModel: the intro is shown pre-keygen, so "Get
    // started" launches the MLDSA single-keygen ceremony — FastVault through
    // password verification, SecureVault through peer discovery. If the vault
    // already holds the quantum key (stale entry point), skip straight to claim.
    fun getStarted() {
        viewModelScope.safeLaunch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with id $vaultId exists")
            if (vault.hasValidMldsaKey()) {
                navigator.route(Route.QbtcClaim(vaultId = vaultId))
                return@safeLaunch
            }
            val hasFastSign = isVaultHasFastSignById(vaultId) && vault.signers.count() == 2
            if (hasFastSign) {
                navigator.route(
                    Route.VerifyExistingVault(
                        name = vault.name,
                        tssAction = TssAction.SingleKeygen,
                        vaultId = vaultId,
                    )
                )
            } else {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.SingleKeygen,
                        vaultName = vault.name,
                        vaultId = vaultId,
                    )
                )
            }
        }
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }
}
