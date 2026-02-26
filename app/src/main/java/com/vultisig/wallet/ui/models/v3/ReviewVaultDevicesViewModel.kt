package com.vultisig.wallet.ui.models.v3

import androidx.annotation.DrawableRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ReviewVaultDevicesUiState(
    val localPartyId: String = "",
    val devices: List<DeviceList> = emptyList(),
)

internal data class DeviceList(
    val name: String = "",
    @DrawableRes val device_image: Int = R.drawable.iphone,
    val id: String = "",
)

internal sealed interface ReviewVaultDevicesEvent {
    data object LooksGood : ReviewVaultDevicesEvent
    data object SomethingsWrong : ReviewVaultDevicesEvent
    data object Back : ReviewVaultDevicesEvent
}

@HiltViewModel
internal class ReviewVaultDevicesViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState = MutableStateFlow(
        ReviewVaultDevicesUiState(
        )
    )

    fun onEvent(event: ReviewVaultDevicesEvent) {
        when (event) {
            ReviewVaultDevicesEvent.LooksGood -> looksGood()
            ReviewVaultDevicesEvent.SomethingsWrong -> back()
            ReviewVaultDevicesEvent.Back -> back()
        }
    }

    private fun looksGood() {

    }

    private fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}
