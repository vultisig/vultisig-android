package com.vultisig.wallet.ui.models.keygen

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SelectVaultTypeUiModel(
    val selectedTypeIndex: Int = 0,
    val triggerAnimation: Boolean = false,
    val vaultType: VaultType = VaultType.Secure,
    val types: List<VaultTypeUiModel> = listOf(
        /* fast&active vaults are temporarily disabled
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
         */
    ),
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

internal data class VaultTypeUiModel(
    val title: UiText,
    @DrawableRes val drawableResId: Int,
    val description: UiText,
    val hasPair: Boolean,
)

@HiltViewModel
internal class SelectVaultTypeViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(SelectVaultTypeUiModel())

    init {
        viewModelScope.launch {
            state.update {
                delay(200)// some delay to load initial page animation
                it.copy(triggerAnimation = true)
            }
        }
    }

    fun selectTab(index: Int) {
        val vaultType = if (index == 0)
            VaultType.Secure else VaultType.Fast
        state.update {
            it.copy(
                vaultType = vaultType,
                selectedTypeIndex = index
            )
        }
    }

    fun start() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.NamingVault(
                    when (state.value.selectedTypeIndex) {
                        /* fast&active vaults are temporarily disabled
                        0 -> VaultSetupType.FAST
                        1 -> VaultSetupType.ACTIVE
                        */
                        else -> VaultSetupType.SECURE
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