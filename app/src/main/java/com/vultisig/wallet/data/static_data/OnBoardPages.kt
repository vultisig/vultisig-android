package com.vultisig.wallet.data.static_data

import com.vultisig.wallet.R
import com.vultisig.wallet.data.on_board.models.OnBoardPage


internal fun getOnBoardingPages() = listOf(
    OnBoardPage(
        image = R.drawable.intro1,
        title = "Meeting",
        description = R.string.intro_1
    ), OnBoardPage(
        image = R.drawable.intro2,
        title = "Coordination",
        description = R.string.intro_2
    ), OnBoardPage(
        image = R.drawable.intro3,
        title = "Dialogue",
        description = R.string.intro_3
    )
)
