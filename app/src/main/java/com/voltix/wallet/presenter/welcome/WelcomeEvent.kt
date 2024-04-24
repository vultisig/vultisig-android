package com.voltix.wallet.presenter.welcome

sealed class WelcomeEvent {
    data object InitPages:WelcomeEvent()
    data object NextPages:WelcomeEvent()
    data object BoardCompleted :WelcomeEvent()
}