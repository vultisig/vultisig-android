package com.vultisig.wallet.ui.models.v3.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SetupVaultInfoUiState(
    val headerLogo: Int = R.drawable.icon_shield_solid,
    val title: UiText = UiText.Empty,
    val subTitle: UiText = UiText.Empty,
    val rive: Int = R.raw.riv_choose_vault,
    val tips: List<SetupVaultInfoTip> = emptyList(),
)


data class SetupVaultInfoTip(
    val logo: Int = R.drawable.icon_shield_solid,
    val title: UiText = UiText.Empty,
    val subTitle: UiText = UiText.Empty
)


internal sealed interface SetupVaultInfoEvent {
    object Next : SetupVaultInfoEvent
    object Back : SetupVaultInfoEvent
}

@HiltViewModel
internal class SetupVaultInfoViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val deviceCount = savedStateHandle.toRoute<Route.SetupVaultInfo>().count
    val uiState = MutableStateFlow(SetupVaultInfoUiState())

    init {
        val state = when (deviceCount) {
            1 -> SetupVaultInfoUiState(
                tips = Tips_1_Device,
                title = UiText.DynamicString("Fast Vault"),
                subTitle = UiText.DynamicString("2-device setup"),
                headerLogo = R.drawable.fast

            )

            2 -> SetupVaultInfoUiState(
                tips = Tips_2_Device,
                title = UiText.DynamicString("Secure Vault"),
                subTitle = UiText.DynamicString("2-device setup"),
                headerLogo = R.drawable.secured_shield
            )

            3 -> SetupVaultInfoUiState(
                tips = Tips_3_Device,
                title = UiText.DynamicString("Secure Vault"),
                subTitle = UiText.DynamicString("3-device setup"),
                headerLogo = R.drawable.icon_shield_solid
            )

            4 -> SetupVaultInfoUiState(
                tips = Tips_4_Device,
                title = UiText.DynamicString("Secure Vault"),
                subTitle = UiText.DynamicString("4+-device vault"),
                headerLogo = R.drawable.icon_shield_solid
            )

            else -> error("is not possible")
        }

        uiState.update { state }

    }

    fun onEvent(
        event: SetupVaultInfoEvent
    ) {
        when (event) {
            SetupVaultInfoEvent.Back -> back()
            SetupVaultInfoEvent.Next -> next()
        }
    }


    private fun next() {
        viewModelScope.launch {
            navigator.route(
                Route.EnterVaultInfo(
                    count = deviceCount
                )
            )
        }
    }

    private fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }


    companion object {
        private val Tips_1_Device = listOf(
            SetupVaultInfoTip(
                R.drawable.signing,
                UiText.StringResource(R.string.vault_setup_1_device_signing),
                UiText.StringResource(R.string.vault_setup_1_device_signing_desc),
            ),
            SetupVaultInfoTip(
                R.drawable.tick_shield,
                UiText.StringResource(R.string.vault_setup_fast_and_secure_setup),
                UiText.StringResource(R.string.vault_setup_fast_and_secure_setup_desc),
            ),
            SetupVaultInfoTip(
                R.drawable.lock,
                UiText.StringResource(R.string.vault_setup_multisig_with_one_device),
                UiText.StringResource(R.string.vault_setup_multisig_with_one_device_desc),
            ),
        )
        private val Tips_2_Device = listOf(
            SetupVaultInfoTip(
                R.drawable.signing,
                UiText.StringResource(R.string.vault_setup_2_device_signing),
                UiText.StringResource(R.string.vault_setup_2_device_signing_desc),
            ),
            SetupVaultInfoTip(
                R.drawable.tick_shield,
                UiText.StringResource(R.string.vault_setup_no_single_point_of_failure),
                UiText.StringResource(R.string.vault_setup_one_device_alone_cant_move_funds),
            ),
            SetupVaultInfoTip(
                R.drawable.lock,
                UiText.StringResource(R.string.vault_setup_ideal_for_simple_cold_storage),
                UiText.StringResource(R.string.vault_setup_perfect_for_users_with_only_2_devices)
            ),
        )
        private val Tips_3_Device = listOf(

            SetupVaultInfoTip(
                R.drawable.signing,
                UiText.StringResource(R.string.vault_setup_2_device_signing),
                UiText.StringResource(R.string.vault_setup_2_device_signing_desc),
            ),
            SetupVaultInfoTip(
                R.drawable.tick_shield,
                UiText.StringResource(R.string.vault_setup_no_single_point_of_failure),
                UiText.StringResource(R.string.vault_setup_one_device_alone_cant_move_funds),
            ),
            SetupVaultInfoTip(
                R.drawable.lock,
                UiText.StringResource(R.string.vault_setup_ideal_for_simple_cold_storage),
                UiText.StringResource(R.string.vault_setup_perfect_balance_desc)
            ),
        )
        private val Tips_4_Device = listOf(
            SetupVaultInfoTip(
                R.drawable.signing,
                UiText.StringResource(R.string.vault_setup_3_device_or_more_signing),
                UiText.StringResource(R.string.vault_setup_3_device_or_more_signing_desc),
            ),
            SetupVaultInfoTip(
                R.drawable.tick_shield,
                UiText.StringResource(R.string.vault_setup_dynamic_device_management),
                UiText.StringResource(R.string.vault_setup_add_as_many_devices_as_you_want),
            ),
            SetupVaultInfoTip(
                R.drawable.lock,
                UiText.StringResource(R.string.vault_setup_built_for_teams_and_treasuries),
                UiText.StringResource(R.string.vault_setup_perfect_balance_desc)
            ),
        )
    }

}