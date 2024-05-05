package com.vultisig.wallet.presenter.welcome

sealed class WelcomeEvent {
    data object InitPages : WelcomeEvent()
    data object BoardCompleted : WelcomeEvent()

    data object NextPages : WelcomeEvent()
}