package com.vultisig.wallet.ui.screens.migration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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

    private val vaultId = savedStateHandle.toRoute<Route.Migration.Onboarding>().vaultId

    // Cached when [upgrade] detects a fast vault, so the sheet callbacks don't have to refetch.
    private var fastVaultName: String? = null

    init {
        viewModelScope.safeLaunch {
            val vault = vaultRepository.get(vaultId) ?: return@safeLaunch
            val vaultType =
                if (vault.isFastVault()) Route.VaultInfo.VaultType.Fast
                else Route.VaultInfo.VaultType.Secure
            state.update { it.copy(vaultType = vaultType) }
        }
    }

    fun upgrade() {
        viewModelScope.safeLaunch {
            val vault =
                requireNotNull(vaultRepository.get(vaultId)) { "Vault $vaultId doesn't exist" }

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
        viewModelScope.safeLaunch { navigator.route(Route.Migration.Password(vaultId = vaultId)) }
    }

    fun continueWithSelfHostedServer() {
        val vaultName = fastVaultName ?: return
        closeServerShareLocationSheet()
        viewModelScope.safeLaunch { routeToPeerMigrate(vaultName) }
    }

    fun dismissServerShareLocationSheet() {
        closeServerShareLocationSheet()
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.navigate(Destination.Back) }
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
