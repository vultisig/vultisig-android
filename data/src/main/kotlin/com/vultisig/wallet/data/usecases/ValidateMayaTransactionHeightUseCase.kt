package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject

interface ValidateMayaTransactionHeightUseCase : suspend (String) -> Boolean

internal class ValidateMayaTransactionHeightUseCaseImpl @Inject constructor(
    private val mayaChainApi: MayaChainApi,
) : ValidateMayaTransactionHeightUseCase {
    override suspend fun invoke(address: String): Boolean = coroutineScope {
        try {
            val latestBlock = async {
                mayaChainApi.getLatestBlock()
            }
            val mayaConstants = async {
                mayaChainApi.getMayaConstants()
            }
            val cacaoProvider = async {
                mayaChainApi.getCacaoProvider(address)
            }

            val currentBlockHeight = latestBlock.await().block.header.height.toLong()
            val depositMaturity = mayaConstants.await().getValue(CACAO_POOL_DEPOSIT_MATURITY_BLOCKS)
            val lastDepositHeight = cacaoProvider.await().lastDepositHeight

            currentBlockHeight - lastDepositHeight > depositMaturity
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    companion object {
        private const val CACAO_POOL_DEPOSIT_MATURITY_BLOCKS = "CACAOPOOLDEPOSITMATURITYBLOCKS"
    }

}
