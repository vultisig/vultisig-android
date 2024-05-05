package com.vultisig.wallet.on_board

import com.vultisig.wallet.data.on_board.models.OnBoardPage
import kotlinx.coroutines.flow.Flow

interface OnBoardRepository {
    suspend fun saveOnBoardingState(completed: Boolean)
    fun readOnBoardingState(): Flow<Boolean>
    fun onBoardPages(): List<OnBoardPage>
}