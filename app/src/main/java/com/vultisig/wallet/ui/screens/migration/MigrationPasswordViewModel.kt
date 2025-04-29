package com.vultisig.wallet.ui.screens.migration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.util.password.InputPasswordViewModelDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MigrationPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Migration.Password>()

    private val delegate = InputPasswordViewModelDelegate(
        vaultId = args.vaultId,
        navigator = navigator,
        vultiSignerRepository = vultiSignerRepository,
        vaultRepository = vaultRepository,
        vaultDataStoreRepository = vaultDataStoreRepository,
    )

    val state = delegate.state
    val passwordFieldState = delegate.passwordFieldState

    fun togglePasswordVisibility() {
        delegate.togglePasswordVisibility()
    }

    fun back() {
        delegate.back()
    }

    fun proceed() {
        viewModelScope.launch {
            if (delegate.checkIfPasswordIsValid()) {
                val vault = vaultRepository.get(args.vaultId)
                    ?: error("No vault with id ${args.vaultId} exists")

                navigator.route(
                    Route.VaultInfo.Email(
                        action = TssAction.Migrate,
                        name = vault.name,
                        vaultId = args.vaultId,
                        password = delegate.password,
                    )
                )
            }
        }
    }

}