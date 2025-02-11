package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.models.onboarding.components.OnboardingPage
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultBackupOnboardingUiModel(
    val vaultType: Route.VaultInfo.VaultType,
    val currentPage: OnboardingPage,
    val pageIndex: Int,
    val pageTotal: Int,
)

@HiltViewModel
internal class VaultBackupOnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Onboarding.VaultBackup>()
    private val vaultId = args.vaultId

    // TODO refactor this into actually configurable model
    private val pages = when (args.vaultType) {
        Route.VaultInfo.VaultType.Fast -> listOf(
            OnboardingPage(),
            OnboardingPage(),
            OnboardingPage(),
        )

        Route.VaultInfo.VaultType.Secure -> listOf(
            OnboardingPage(),
            OnboardingPage(),
        )
    }


    val state = MutableStateFlow(
        VaultBackupOnboardingUiModel(
            vaultType = args.vaultType,
            currentPage = pages.first(),
            pageIndex = 0,
            pageTotal = pages.size
        )
    )

    fun next() {
        viewModelScope.launch {
            val nextAnimation = pages.getOrNull(state.value.pageIndex + 1)
            if (nextAnimation != null) {
                state.update {
                    it.copy(
                        currentPage = nextAnimation,
                        pageIndex = it.pageIndex + 1
                    )
                }
            } else {
                when (args.vaultType) {
                    Route.VaultInfo.VaultType.Fast -> {
                        navigator.route(
                            Route.FastVaultVerification(
                                vaultId = vaultId,
                                pubKeyEcdsa = args.pubKeyEcdsa,
                                email = requireNotNull(args.email),
                            )
                        )
                    }

                    Route.VaultInfo.VaultType.Secure -> {
                        navigator.route(
                            Route.BackupVault(
                                vaultId = vaultId,
                                vaultType = args.vaultType,
                            )
                        )
                    }
                }
            }
        }
    }

    fun back() {}
}