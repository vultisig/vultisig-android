package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.OnBoardPage
import kotlinx.coroutines.flow.Flow

interface OnBoardRepository {
    suspend fun saveOnBoardingState(completed: Boolean)
    fun readOnBoardingState(): Flow<Boolean>
    fun onBoardPages(): List<OnBoardPage>
}