package com.vultisig.wallet.ui.models.v3.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountViewModel.Companion.Tips
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class ChooseDeviceCountUiState(
    val deviceCount: Int = 1,
    val tips: List<DeviceCountTip> = Tips,
)

internal sealed interface ChooseDeviceCountUiEvent {
    data object Back : ChooseDeviceCountUiEvent

    data object IncreaseCount : ChooseDeviceCountUiEvent

    data object DecreaseCount : ChooseDeviceCountUiEvent

    data object Next : ChooseDeviceCountUiEvent
}

@Immutable
internal data class DeviceCountTip(
    val logo: Int,
    val title: UiText,
    val subTitle: UiText,
    val subTitleHighlight: UiText? = null,
)

@HiltViewModel
internal class ChooseDeviceCountViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val navigator: Navigator<Destination>) :
    ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ChooseVaultCount>()

    private val _uiState = MutableStateFlow(ChooseDeviceCountUiState())
    val uiState = _uiState.asStateFlow()

    fun handleEvent(event: ChooseDeviceCountUiEvent) {
        when (event) {
            ChooseDeviceCountUiEvent.Back -> back()
            ChooseDeviceCountUiEvent.DecreaseCount -> decreaseCount()
            ChooseDeviceCountUiEvent.IncreaseCount -> increaseCount()
            ChooseDeviceCountUiEvent.Next -> next()
        }
    }

    private fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun increaseCount() {
        val newCount = (_uiState.value.deviceCount + 1).coerceIn(1, 4)
        _uiState.update { it.copy(deviceCount = newCount) }
    }

    private fun decreaseCount() {
        val newCount = (_uiState.value.deviceCount - 1).coerceIn(1, 4)
        _uiState.update { it.copy(deviceCount = newCount) }
    }

    private fun next() {
        viewModelScope.launch {
            val count = _uiState.value.deviceCount
            navigator.route(Route.SetupVaultInfo(count = count, tssAction = args.tssAction))
        }
    }

    companion object {
        val Tips =
            listOf(
                DeviceCountTip(
                    logo = R.drawable.fast,
                    title = UiText.StringResource(R.string.welcome_fast_and_easy),
                    subTitle = UiText.StringResource(R.string.welcome_fast_and_easy_desc),
                    subTitleHighlight =
                        UiText.StringResource(R.string.welcome_fast_and_easy_highlight),
                ),
                DeviceCountTip(
                    logo = R.drawable.shield,
                    title = UiText.StringResource(R.string.welcome_only_your_devices),
                    subTitle = UiText.StringResource(R.string.welcome_only_your_devices_desc),
                    subTitleHighlight =
                        UiText.StringResource(R.string.welcome_only_your_devices_highlight),
                ),
                DeviceCountTip(
                    logo = R.drawable.balance,
                    title = UiText.StringResource(R.string.welcome_best_balance),
                    subTitle = UiText.StringResource(R.string.welcome_best_balance_desc),
                    subTitleHighlight =
                        UiText.StringResource(R.string.welcome_best_balance_highlight),
                ),
                DeviceCountTip(
                    logo = R.drawable.maximun_security,
                    title = UiText.StringResource(R.string.welcome_maximum_security),
                    subTitle = UiText.StringResource(R.string.welcome_maximum_security_desc),
                    subTitleHighlight =
                        UiText.StringResource(R.string.welcome_maximum_security_highlight),
                ),
            )
    }
}
