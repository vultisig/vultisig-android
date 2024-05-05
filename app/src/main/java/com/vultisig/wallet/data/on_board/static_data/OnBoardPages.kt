package com.vultisig.wallet.data.on_board.static_data

import com.vultisig.wallet.R
import com.vultisig.wallet.data.on_board.models.OnBoardPage


internal fun getOnBoardingPages() = listOf(
    OnBoardPage(
        image = R.drawable.intro1,
        title = "Meeting",
        description = "Vultisig is a secure, multi-device crypto vault, compatible with 30+ chains and 10,000+ tokens. Vultisig is fully self-custodial."
    ), OnBoardPage(
        image = R.drawable.intro2,
        title = "Coordination",
        description = "Vultisig does not track your activities or require any registrations. Vultisig is fully open-source, ensuring transparency and trust."
    ), OnBoardPage(
        image = R.drawable.intro3,
        title = "Dialogue",
        description = "Vultisig is audited and secure. Join thousands of users who trust Vultisig with their digital assets. "
    )
)
