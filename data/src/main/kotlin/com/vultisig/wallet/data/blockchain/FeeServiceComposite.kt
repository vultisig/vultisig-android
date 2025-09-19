package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeeServiceComposite @Inject constructor(
    @EthereumFee private val ethereumFeeService: FeeService,
    @PolkadotFee private val polkadotFeeService: FeeService,
    @RippleFee private val rippleFeeService: FeeService,
    @SuiFee private val suiFeeService: FeeService,
    @TonFee private val tonFeeService: FeeService,
) : FeeService {
    
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val service = getFeeServiceForChain(chain)
        
        Timber.d("Calculating fees for chain: ${chain.name} using ${service::class.simpleName}")
        
        return try {
            service.calculateFees(transaction)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate fees for ${chain.name}, falling back to default")
            calculateDefaultFees(transaction)
        }
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val service = getFeeServiceForChain(chain)
        
        Timber.d("Calculating default fees for chain: ${chain.name}")
        
        return service.calculateDefaultFees(transaction)
    }
    
    private fun getFeeServiceForChain(chain: Chain): FeeService {
        return when (chain.standard) {
            TokenStandard.EVM -> ethereumFeeService
            TokenStandard.SUBSTRATE -> polkadotFeeService
            TokenStandard.RIPPLE -> rippleFeeService
            TokenStandard.SUI -> suiFeeService
            TokenStandard.TON -> tonFeeService
            else -> error("Not Supported ")
        }
    }
}