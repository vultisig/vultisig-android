package com.voltix.wallet.on_board.use_cases

import com.voltix.wallet.domain.on_board.models.OnBoardPage
import com.voltix.wallet.on_board.repository.OnBoardRepository
import javax.inject.Inject

class BoardPages @Inject constructor(private val onBoardRepository: OnBoardRepository) {
    operator fun invoke(): List<OnBoardPage> = onBoardRepository.onBoardPages()
}