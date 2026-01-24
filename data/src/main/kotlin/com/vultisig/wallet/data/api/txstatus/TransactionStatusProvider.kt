package com.vultisig.wallet.data.api.txstatus


import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import javax.inject.Inject

sealed class TransactionResult {
    object Confirmed : TransactionResult()
    object Pending : TransactionResult()
    object NotFound : TransactionResult()
    data class Failed(val reason: String) : TransactionResult()
}

interface TransactionStatusRepository {
    suspend fun checkTransactionStatus(txHash: String, chain: Chain): TransactionResult
}

class TransactionStatusRepositoryImpl @Inject constructor(
    @param:EvmTxStatus private val evmProvider: TransactionStatusProvider,
    @param:UtxoTxStatus private val utxoProvider: TransactionStatusProvider,
    @param:CosmosTxStatus private val cosmosProvider: TransactionStatusProvider,
    @param:ThorChainTxStatus private val thorChainProvider: TransactionStatusProvider,
    @param:SolanaTxStatus private val solanaProvider: TransactionStatusProvider,
    @param:SuiTxStatus private val suiProvider: TransactionStatusProvider,
    @param:TonTxStatus private val tonProvider: TransactionStatusProvider,
    @param:PolkadotTxStatus private val polkadotProvider: TransactionStatusProvider,
    @param:RippleTxStatus private val rippleProvider: TransactionStatusProvider,
    @param:TronTxStatus private val tronProvider: TransactionStatusProvider,
) : TransactionStatusRepository {
    private fun getProvider(chain: Chain) = when (chain.standard) {
        TokenStandard.EVM -> evmProvider
        TokenStandard.UTXO -> utxoProvider
        TokenStandard.COSMOS -> cosmosProvider
        TokenStandard.THORCHAIN -> thorChainProvider
        TokenStandard.SOL -> solanaProvider
        TokenStandard.SUBSTRATE -> polkadotProvider
        TokenStandard.SUI -> suiProvider
        TokenStandard.TON -> tonProvider
        TokenStandard.RIPPLE -> rippleProvider
        TokenStandard.TRC20 -> tronProvider
    }

    override suspend fun checkTransactionStatus(
        txHash: String,
        chain: Chain
    ): TransactionResult {
        val provider =  getProvider(chain)
        return provider.checkStatus(
            txHash = txHash,
            chain = chain
        )
    }
}

interface TransactionStatusProvider {
     suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult
}


