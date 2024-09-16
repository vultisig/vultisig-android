package com.vultisig.wallet.ui.models.keygen

import androidx.annotation.DrawableRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SelectVaultTypeUiModel(
    val selectedTypeIndex: Int = 0,
    val types: List<VaultTypeUiModel> = listOf(
        VaultTypeUiModel(
            title = UiText.StringResource(R.string.select_vault_type_fast_title),
            drawableResId = R.drawable.vault_type_fast,
            description = UiText.StringResource(R.string.select_vault_type_fast_description),
            hasPair = false,
        ),
        VaultTypeUiModel(
            title = UiText.StringResource(R.string.select_vault_type_active_title),
            drawableResId = R.drawable.vault_type_active,
            description = UiText.StringResource(R.string.select_vault_type_active_description),
            hasPair = true,
        ),
        VaultTypeUiModel(
            title = UiText.StringResource(R.string.select_vault_type_secure_title),
            drawableResId = R.drawable.vault_type_secure,
            description = UiText.StringResource(R.string.select_vault_type_secure_description),
            hasPair = true,
        ),
    ),
)

internal data class VaultTypeUiModel(
    val title: UiText,
    @DrawableRes val drawableResId: Int,
    val description: UiText,
    val hasPair: Boolean,
)

@HiltViewModel
internal class SelectVaultTypeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String? = savedStateHandle.get(Destination.ARG_VAULT_ID)

    val state = MutableStateFlow(SelectVaultTypeUiModel())

    fun selectTab(index: Int) {
        state.update {
            it.copy(
                selectedTypeIndex = index,
            )
        }
    }

    fun start() {
        when (state.value.selectedTypeIndex) {
            0 -> {
                // Fast
                viewModelScope.launch {
                    navigator.navigate(
                        Destination.NamingVault(
                            VaultSetupType.FAST,
                        )
                    )
                }
            }

            1 -> {
                // Active
                viewModelScope.launch {
                    navigator.navigate(
                        Destination.NamingVault(
                            VaultSetupType.ACTIVE,
                        )
                    )
                }
            }

            2 -> {
                viewModelScope.launch {
                    navigator.navigate(Destination.NamingVault(VaultSetupType.SECURE))
                }
            }
        }
    }

    fun pair() {
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

}