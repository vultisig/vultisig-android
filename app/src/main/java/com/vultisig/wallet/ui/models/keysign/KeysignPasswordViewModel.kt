package com.vultisig.wallet.ui.models.keysign

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.util.password.InputPasswordViewModelDelegate
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeysignPasswordUiModel(
    val isPasswordVisible: Boolean = false,
    val passwordError: UiText? = null,
    val passwordHint: UiText? = null,
)

@HiltViewModel
internal class KeysignPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Keysign.Password>()
    private val transactionId: TransactionId = args.transactionId

    private val delegate = InputPasswordViewModelDelegate(
        vaultId = args.vaultId,
        navigator = navigator,
        vultiSignerRepository = vultiSignerRepository,
        vaultRepository = vaultRepository,
        vaultDataStoreRepository = vaultDataStoreRepository,
    )

    val state = MutableStateFlow(KeysignPasswordUiModel())

    val passwordFieldState = delegate.passwordFieldState

    init {
        delegate.state
            .onEach { newState ->
                state.update { newState }
            }
            .launchIn(viewModelScope)
    }

    fun togglePasswordVisibility() {
        delegate.togglePasswordVisibility()
    }

    fun back() {
        delegate.back()
    }

    fun proceed() {
        viewModelScope.launch {
            if (delegate.checkIfPasswordIsValid()) {
                navigator.route(
                    Route.Keysign.Keysign(
                        transactionId = transactionId,
                        password = delegate.password,
                        txType = args.txType,
                    )
                )
            }
        }
    }

}