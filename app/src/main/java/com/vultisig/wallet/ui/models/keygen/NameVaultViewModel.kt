package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateRandomUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class NameVaultUiModel(
    val errorMessage: UiText? = null,
    val isNextButtonEnabled: Boolean = false,
)

@HiltViewModel
internal class NameVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val uniqueName: GenerateRandomUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state = MutableStateFlow(NameVaultUiModel())

    val nameFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.VaultInfo.Name>()

    private var vaultNamesList = emptyList<String>()

    init {
        viewModelScope.launch {
            vaultNamesList = vaultRepository.getAll().map { it.name }

            nameFieldState.textAsFlow().collectLatest {
                validate()
            }
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = nameFieldState.text.toString()
        val errorMessage = if (!isNameValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        val isNextButtonEnabled = errorMessage == null
        state.update {
            it.copy(
                errorMessage = errorMessage,
                isNextButtonEnabled = isNextButtonEnabled,
            )
        }
    }

    private fun isNameValid(name: String): Boolean {
        return isNameLengthValid(name)
    }

    fun navigateToEmail() {
        val expectedName = nameFieldState.text.toString()
        if (!isNameValid(expectedName))
            return
        viewModelScope.launch {
            val name = uniqueName(
                expectedName.ifEmpty {
                    context.getString(R.string.naming_vault_placeholder_fast_vault)
                },
                vaultNamesList
            )

            when (args.vaultType) {
                VaultType.Fast -> {
                    navigator.route(
                        Route.VaultInfo.Email(name)
                    )
                }

                VaultType.Secure -> {
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            vaultName = name,
                        )
                    )
                }
            }
        }
    }

    fun clearInput() {
        nameFieldState.clearText()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}