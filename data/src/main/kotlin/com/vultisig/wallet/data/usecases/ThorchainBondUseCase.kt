package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import javax.inject.Inject


fun interface ThorchainBondUseCase {
    suspend operator fun invoke()
}

class ThorchainBondUseCaseImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
): ThorchainBondUseCase {
    override suspend fun invoke() {
        TODO("Not yet implemented")
    }
}