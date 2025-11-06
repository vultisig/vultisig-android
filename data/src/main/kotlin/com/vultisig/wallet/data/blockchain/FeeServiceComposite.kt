package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeeServiceComposite @Inject constructor(
    @EthereumFee private val ethereumFeeService: FeeService,
    @ZkSyncFee private val zkFeeService: FeeService,
    @PolkadotFee private val polkadotFeeService: FeeService,
    @RippleFee private val rippleFeeService: FeeService,
    @SuiFee private val suiFeeService: FeeService,
    @TonFee private val tonFeeService: FeeService,
    @TronFee private val tronFeeService: FeeService,
    @SolanaFee private val solanaFeeService: FeeService,
    @ThorFee private val thorchainFeeService: FeeService,
    @CosmosFee private val cosmosFeeService: FeeService,
) : FeeService {
    
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        val chain = transaction.coin.chain
        val service = getFeeServiceForChain(chain)
        
        Timber.d("Calculating fees for chain: ${chain.name} using ${service::class.simpleName}")
        
        return try {
            service.calculateFees(transaction)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.e(e, "FeeServiceComposite: Coroutine has been cancelled")
            throw e
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

    @Deprecated("Only used for ethereum, to remove upcoming PR")
    override suspend fun calculateFees(chain: Chain, limit: BigInteger, isSwap: Boolean, to: String?): Fee {
        require(chain.standard == TokenStandard.EVM) {
            "Unsupported method for ${chain.standard.name}"
        }

        return ethereumFeeService.calculateFees(chain, limit, isSwap, to)
    }
    
    private fun getFeeServiceForChain(chain: Chain): FeeService {
        return when {
            chain == Chain.ZkSync -> zkFeeService
            chain.standard == TokenStandard.COSMOS -> cosmosFeeService
            chain.standard == TokenStandard.EVM -> ethereumFeeService
            chain.standard == TokenStandard.SUBSTRATE -> polkadotFeeService
            chain.standard == TokenStandard.RIPPLE -> rippleFeeService
            chain.standard == TokenStandard.SUI -> suiFeeService
            chain.standard == TokenStandard.TON -> tonFeeService
            chain.standard == TokenStandard.TRC20 -> tronFeeService
            chain.standard == TokenStandard.SOL -> solanaFeeService
            chain.standard == TokenStandard.THORCHAIN -> thorchainFeeService
            else -> error("FeeServiceComposite not supported chain: ${chain.name}")
        }
    }
}