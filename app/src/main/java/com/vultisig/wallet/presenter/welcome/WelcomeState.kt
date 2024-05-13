package com.vultisig.wallet.presenter.welcome

import com.vultisig.wallet.data.models.OnBoardPage

data class WelcomeState(
    val pages: List<OnBoardPage> = emptyList(),
)