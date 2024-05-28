package com.vultisig.wallet.presenter.common

import com.vultisig.wallet.ui.navigation.Screen

sealed class UiEvent {
    data class NavigateTo(val screen: Screen) : UiEvent()
    data class ScrollToNextPage(val screen: Screen) : UiEvent()
}