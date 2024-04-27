package com.voltix.wallet.data.on_board.mappers

import com.voltix.wallet.data.on_board.models.OnBoardPage

fun OnBoardPage.toDomain(
): OnBoardPage = OnBoardPage(image, title, description)