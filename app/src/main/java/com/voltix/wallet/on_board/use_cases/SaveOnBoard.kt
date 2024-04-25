package com.voltix.wallet.domain.on_board.use_cases

import com.voltix.wallet.on_board.repository.OnBoardRepository
import javax.inject.Inject

class SaveOnBoard @Inject constructor(private val onBoardRepository: OnBoardRepository) {
    suspend operator fun invoke(completed: Boolean) {
        onBoardRepository.saveOnBoardingState(completed)
    }
}