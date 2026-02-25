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
            )

            2 -> SetupVaultInfoUiState(
                tips = Tips_2_Device,
            )

            3 -> SetupVaultInfoUiState(
                tips = Tips_3_Device,
            )

            4 -> SetupVaultInfoUiState(
                tips = Tips_4_Device,
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
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
        )
        private val Tips_2_Device = listOf(

            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
        )
        private val Tips_3_Device = listOf(

            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
        )
        private val Tips_4_Device = listOf(
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
            SetupVaultInfoTip(),
        )
    }

}