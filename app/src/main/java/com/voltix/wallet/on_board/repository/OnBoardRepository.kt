package com.voltix.wallet.on_board.repository

import com.voltix.wallet.domain.on_board.models.OnBoardPage
import kotlinx.coroutines.flow.Flow

interface OnBoardRepository {
    suspend fun saveOnBoardingState(completed: Boolean)
    fun readOnBoardingState(): Flow<Boolean>
    fun onBoardPages(): List<OnBoardPage>
}