package com.voltix.wallet.presenter.common

import com.voltix.wallet.presenter.navigation.Screen

sealed class UiEvent {
    data object PopBackStack : UiEvent()
    data class NavigateTo(val screen: Screen) : UiEvent()
}