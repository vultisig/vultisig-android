package com.voltix.wallet.data.on_board.static_data

import com.voltix.wallet.R
import com.voltix.wallet.data.on_board.models.OnBoardPage


internal fun getOnBoardingPages() = listOf(
    OnBoardPage(
        image = R.drawable.intro1,
        title = "Meeting",
        description = "Voltix is a secure, multi-device crypto vault, compatible with 30+ chains and 10,000+ tokens. Voltix is fully self-custodial."
    ), OnBoardPage(
        image = R.drawable.intro2,
        title = "Coordination",
        description = "Voltix does not track your activities or require any registrations. Voltix is fully open-source, ensuring transparency and trust."
    ), OnBoardPage(
        image = R.drawable.intro3,
        title = "Dialogue",
        description = "Voltix is audited and secure. Join thousands of users who trust Voltix with their digital assets. "
    )
)
