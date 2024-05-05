package com.vultisig.wallet.data.on_board.mappers

import com.vultisig.wallet.data.on_board.models.OnBoardPage

fun OnBoardPage.toDomain(): OnBoardPage = OnBoardPage(image, title, description)