package com.vultisig.wallet.ui.screens.migration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class MigrationOnboardingUiModel(
    val vaultType: Route.VaultInfo.VaultType = Route.VaultInfo.VaultType.Secure,
    val isServerShareLocationSheetVisible: Boolean = false,
)

@HiltViewModel
internal class MigrationOnboardingViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val state = MutableStateFlow(MigrationOnboardingUiModel())

    private val args = savedStateHandle.toRoute<Route.Migration.Onboarding>()
    private val vaultId = args.vaultId

    // Cached when [upgrade] detects a fast vault, so the sheet callbacks don't need to refetch.
    private var fastVaultName: String? = null

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId = vaultId)?.let { vault ->
                state.update {
                    it.copy(
                        vaultType =
                            if (vault.isFastVault()) {
                                Route.VaultInfo.VaultType.Fast
                            } else {
                                Route.VaultInfo.VaultType.Secure
                            }
                    )
                }
            }
        }
    }

    fun upgrade() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("Vault with $vaultId doesn't exist")

            if (vault.isFastVault()) {
                // The vault has a server signer, but the user may have imported that share onto
                // another device they own. Ask before silently hitting the online VultiServer.
                fastVaultName = vault.name
                state.update { it.copy(isServerShareLocationSheetVisible = true) }
            } else {
                routeToPeerMigrate(vault.name)
            }
        }
    }

    fun continueWithOnlineVultiServer() {
        closeServerShareLocationSheet()
        viewModelScope.launch { navigator.route(Route.Migration.Password(vaultId = vaultId)) }
    }

    fun continueWithSelfHostedServer() {
        val vaultName = fastVaultName ?: return
        closeServerShareLocationSheet()
        viewModelScope.launch { routeToPeerMigrate(vaultName) }
    }

    fun dismissServerShareLocationSheet() {
        closeServerShareLocationSheet()
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun closeServerShareLocationSheet() {
        fastVaultName = null
        state.update { it.copy(isServerShareLocationSheetVisible = false) }
    }

    private suspend fun routeToPeerMigrate(vaultName: String) {
        navigator.route(
            Route.Keygen.PeerDiscovery(
                action = TssAction.Migrate,
                vaultName = vaultName,
                vaultId = vaultId,
            )
        )
    }
}
