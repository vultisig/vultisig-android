package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

internal data class VaultConfirmationUiModel(
    val vaultInfo: Route.VaultInfo.VaultType,
    val action: TssAction?,
)

@HiltViewModel
internal class VaultConfirmationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VaultConfirmation>()

    val state = MutableStateFlow(
        VaultConfirmationUiModel(
            vaultInfo = args.vaultType,
            action = args.action,
        )
    )

    init {
        viewModelScope.launch {
            delay(5.seconds)

            when (args.action) {
                TssAction.Migrate -> {
                    navigator.route(
                        route = Route.Home(),
                        opts = NavigationOptions(
                            clearBackStack = true,
                        )
                    )
                }

                else -> {
                    navigator.route(
                        route = Route.VaultBackupSummary(
                            vaultId = args.vaultId,
                            vaultType = args.vaultType,
                        ),
                        opts = NavigationOptions(
                            popUpToRoute = Route.ChooseVaultType::class,
                            inclusive = true,
                        )
                    )
                }
            }
        }
    }

}