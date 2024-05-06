package com.vultisig.wallet.data.on_board.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class OnBoardPage(
    @DrawableRes
    val image: Int,
    val title: String,
    @StringRes val description: Int
) 