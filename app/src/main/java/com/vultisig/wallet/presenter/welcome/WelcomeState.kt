package com.vultisig.wallet.presenter.welcome

import com.vultisig.wallet.data.on_board.models.OnBoardPage

data class WelcomeState(
    val pages: List<OnBoardPage> = emptyList(),
)