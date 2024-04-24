package com.voltix.wallet.data.on_board.mappers

import com.voltix.wallet.data.on_board.models.OnBoardPageData
import com.voltix.wallet.domain.on_board.models.OnBoardPage

fun OnBoardPageData.toDomain(
): OnBoardPage = OnBoardPage(image, title, description)