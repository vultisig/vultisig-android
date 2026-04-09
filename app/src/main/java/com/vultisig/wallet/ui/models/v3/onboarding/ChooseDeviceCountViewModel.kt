package com.vultisig.wallet.ui.models.v3.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface ChooseDeviceCountUiEvent {
    data object Back : ChooseDeviceCountUiEvent

    data class IndexChanged(val index: Int) : ChooseDeviceCountUiEvent

    data object Next : ChooseDeviceCountUiEvent
}

@HiltViewModel
internal class ChooseDeviceCountViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val navigator: Navigator<Destination>) :
    ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ChooseVaultCount>()

    private val deviceCount = MutableStateFlow(1)

    fun handleEvent(event: ChooseDeviceCountUiEvent) {
        when (event) {
            ChooseDeviceCountUiEvent.Back -> back()
            is ChooseDeviceCountUiEvent.IndexChanged -> changeCount(event.index)
            ChooseDeviceCountUiEvent.Next -> next()
        }
    }

    private fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun changeCount(index: Int) {
        // "Index" property of rive starts from 0
        deviceCount.update { index + 1 }
    }

    private fun next() {
        viewModelScope.launch {
            val count = deviceCount.value
            navigator.route(Route.SetupVaultInfo(count = count, tssAction = args.tssAction))
        }
    }
}
