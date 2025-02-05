package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SelectVaultTypeUiModel(
    val vaultType: VaultType = VaultType.Secure,
)

internal sealed class VaultType(
    val title: UiText,
    val desc1: UiText,
    val desc2: UiText,
    val desc3: UiText,
) {
    data object Secure : VaultType(
        title = UiText.StringResource(R.string.select_vault_type_secure_title),
        desc1 = UiText.StringResource(R.string.select_vault_type_secure_desc_1),
        desc2 = UiText.StringResource(R.string.select_vault_type_secure_desc_2),
        desc3 = UiText.StringResource(R.string.select_vault_type_secure_desc_3),
    )

    data object Fast : VaultType(
        title = UiText.StringResource(R.string.select_vault_type_fast_title),
        desc1 = UiText.StringResource(R.string.select_vault_type_fast_desc_1),
        desc2 = UiText.StringResource(R.string.select_vault_type_fast_desc_2),
        desc3 = UiText.StringResource(R.string.select_vault_type_fast_desc_3),
    )
}

@HiltViewModel
internal class ChooseVaultViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(SelectVaultTypeUiModel())

    fun selectTab(type: VaultType) {
        clickOnce(coolDownPeriod = 1500L) {
            state.update {
                it.copy(
                    vaultType = type,
                )
            }
        }.invoke()
    }

    fun navigateToBack() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun start() {
        viewModelScope.launch {
            navigator.route(
                Route.VaultInfo.Name(
                    when (state.value.vaultType) {
                        VaultType.Secure -> Route.VaultInfo.VaultType.Secure
                        VaultType.Fast -> Route.VaultInfo.VaultType.Fast
                    }
                )
            )
        }
    }

    fun pair() {
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

}