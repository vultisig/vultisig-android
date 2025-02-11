package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultBackupSummaryUiModel(
    val isConsentChecked: Boolean = false,
    val vaultType: Route.VaultInfo.VaultType,
)

@HiltViewModel
internal class VaultBackupSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VaultBackupSummary>()

    val state = MutableStateFlow(
        VaultBackupSummaryUiModel(
            vaultType = args.vaultType
        )
    )

    fun toggleCheck(isChecked: Boolean) {
        state.update { it.copy(isConsentChecked = isChecked) }
    }

    fun next() {
        if (state.value.isConsentChecked) {
            viewModelScope.launch {
                navigator.navigate(
                    dst = Destination.Home(),
                    opts = NavigationOptions(
                        popUpToRoute = Route.VaultBackupSummary::class,
                        inclusive = true,
                    ),
                )
            }
        }
    }

}