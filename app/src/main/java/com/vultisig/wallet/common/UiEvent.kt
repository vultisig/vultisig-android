package com.vultisig.wallet.common

sealed class UiEvent {
    data object NavigateUp:UiEvent()
    data class NavigateToScreen(val route:String):UiEvent()
}