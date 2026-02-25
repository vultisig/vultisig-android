package com.vultisig.wallet.ui.models.v3.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountViewModel.Companion.Tips
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChooseDeviceCountUiState(
    val deviceCount: Int = 1,
    val tips: List<DeviceCountTip> = Tips,
)

sealed interface ChooseDeviceCountUiEvent {
    object IncreaseCount : ChooseDeviceCountUiEvent
    object DecreaseCount : ChooseDeviceCountUiEvent
    object Next : ChooseDeviceCountUiEvent
}

data class DeviceCountTip(
    val logo: Int,
    val title: UiText,
    val subTitle: UiText,
)


@HiltViewModel
internal class ChooseDeviceCountViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val uiState = MutableStateFlow(ChooseDeviceCountUiState())

    fun handleEvent(
        event: ChooseDeviceCountUiEvent
    ) {
        when (event) {
            ChooseDeviceCountUiEvent.DecreaseCount -> decreaseCount()
            ChooseDeviceCountUiEvent.IncreaseCount -> increaseCount()
            ChooseDeviceCountUiEvent.Next -> next()
        }
    }

    private fun increaseCount() {
        val vaultCount = uiState.value.deviceCount + 1
        uiState.update {
            it.copy(deviceCount = vaultCount)
        }
    }

    private fun decreaseCount() {
        val vaultCount = uiState.value.deviceCount - 1
        uiState.update {
            it.copy(deviceCount = vaultCount)
        }
    }

    private fun next() {
        viewModelScope.launch {
            navigator.route(
                Route.SetupVaultInfo(
                    count = uiState.value.deviceCount
                )
            )
        }
    }

    companion object {
        val Tips = listOf(
            DeviceCountTip(
                logo = R.drawable.icon_shield_solid,
                title = UiText.StringResource(R.string.faq_settings_q1),
                subTitle = UiText.StringResource(R.string.faq_settings_a1),
            ),
            DeviceCountTip(
                logo = R.drawable.icon_shield_solid,
                title = UiText.StringResource(R.string.faq_settings_q2),
                subTitle = UiText.StringResource(R.string.faq_settings_a2),
            ),
            DeviceCountTip(
                logo = R.drawable.icon_shield_solid,
                title = UiText.StringResource(R.string.faq_settings_q3),
                subTitle = UiText.StringResource(R.string.faq_settings_a3),
            ),
            DeviceCountTip(
                logo = R.drawable.icon_shield_solid,
                title = UiText.StringResource(R.string.faq_settings_q4),
                subTitle = UiText.StringResource(R.string.faq_settings_a4),
            ),
        )
    }
}

