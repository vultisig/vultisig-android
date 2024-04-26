package com.voltix.wallet.on_board.use_cases

import com.voltix.wallet.on_board.repository.OnBoardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReadOnBoard @Inject constructor(private val onBoardRepository: OnBoardRepository) {
    operator fun invoke(): Flow<Boolean> {
        return onBoardRepository.readOnBoardingState()
    }
}